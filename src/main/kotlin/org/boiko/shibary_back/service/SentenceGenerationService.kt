package org.boiko.shibary_back.service

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.boiko.shibary_back.client.ChadApiClient
import org.boiko.shibary_back.client.LibreTranslateClient
import org.boiko.shibary_back.config.ChadApiProperties
import org.boiko.shibary_back.dto.SentenceResponse
import org.boiko.shibary_back.dto.TranslatedSentence
import org.boiko.shibary_back.dto.WordInfoResponse
import org.boiko.shibary_back.model.Sentence
import org.boiko.shibary_back.model.WordInfo
import org.boiko.shibary_back.repository.SentenceRepository
import org.boiko.shibary_back.repository.WordInfoRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service

class SentenceGenerationService(
  private val sentenceRepository: SentenceRepository,
  private val wordInfoRepository: WordInfoRepository,
  private val chadApiClient: ChadApiClient,
  private val chadApiProperties: ChadApiProperties,
  private val libreTranslateClient: LibreTranslateClient,
  private val objectMapper: ObjectMapper
) : DisposableBean {

  private val log = LoggerFactory.getLogger(javaClass)

  private val backgroundScope = CoroutineScope(
    SupervisorJob() + Dispatchers.IO + CoroutineName("sentence-bg")
  )

  private val backgroundGenerationWords = ConcurrentHashMap.newKeySet<String>()
  private val wordLocks = ConcurrentHashMap<String, ReentrantLock>()

  companion object {

    private const val PREFETCH_DISTANCE_FROM_END = 2

    private const val PROMPT_TEMPLATE =
      """Generate exactly %d unique simple English sentences (about 5 words each) using the word "%s".
               Return ONLY a valid JSON array of strings with no extra text.
               Each sentence must contain the given word.
               Example for word "apples": ["I like apples.","She eats apples."]"""

    private const val WORD_INFO_PROMPT_TEMPLATE =
      """For the English word "%s" provide a short definition, a list of synonyms and a list of antonyms.
               Return ONLY a valid JSON object with no extra text, in the following exact format:
               {"definition": "<short definition>", "synonyms": ["s1","s2"], "antonyms": ["a1","a2"]}
               If there are no synonyms or antonyms, return an empty array for them."""
  }

  override fun destroy() {
    backgroundScope.cancel()
  }

  fun getSentences(word: String, count: Int, offset: Int?): SentenceResponse {
    val normalizedWord = word.trim().lowercase()
    val requestedOffset = offset ?: 0
    val generationBatchSize = chadApiProperties.sentenceCount

    return withWordLock(normalizedWord) {
      val savedCount = sentenceRepository.countByWord(normalizedWord).toInt()

      if (savedCount > 0) {
        val savedSentences = sentenceRepository.findByWord(normalizedWord, count, requestedOffset)
        val wordRu = savedSentences.firstOrNull()?.wordRu
          ?: sentenceRepository.findByWord(normalizedWord, 1, 0).firstOrNull()?.wordRu
          ?: translateSafely(normalizedWord)

        val requestEndOffset = requestedOffset + count
        val missingForRequest = (requestEndOffset - savedCount).coerceAtLeast(0)
        val shouldPrefetch = savedCount - requestEndOffset <= PREFETCH_DISTANCE_FROM_END

        if (missingForRequest == 0) {
          if (shouldPrefetch) {
            log.info("Prefetching sentences for word $normalizedWord, offset $requestedOffset, count $count")
            scheduleBackgroundGeneration(normalizedWord, wordRu, generationBatchSize)
          }
          return@withWordLock buildSentenceResponse(normalizedWord, wordRu, savedSentences)
        }

        val generatedPairs = generateTranslateAndSaveSync(
          word = normalizedWord,
          wordRu = wordRu,
          count = missingForRequest
        )
        val backgroundCount = (generationBatchSize - missingForRequest).coerceAtLeast(0)
        scheduleBackgroundGeneration(normalizedWord, wordRu, backgroundCount)

        return@withWordLock SentenceResponse(
          word = normalizedWord,
          wordRu = wordRu,
          sentences = savedSentences.map { TranslatedSentence(it.text, it.textRu) } + generatedPairs.take(count - savedSentences.size)
        )
      }

      val syncCount = count.coerceAtMost(generationBatchSize)
      val asyncCount = generationBatchSize - syncCount
      val wordRu = translateSafely(normalizedWord)
      val syncPairs = generateTranslateAndSaveSync(normalizedWord, wordRu, syncCount)

      backgroundScope.launch {
        supervisorScope {
          launch { generateAndSaveWordInfo(normalizedWord) }
          if (asyncCount > 0) {
            launch { scheduleBackgroundGeneration(normalizedWord, wordRu, asyncCount) }
          }
        }
      }

      SentenceResponse(
        word = normalizedWord,
        wordRu = wordRu,
        sentences = syncPairs
      )
    }
  }

  fun getWordInfo(word: String): WordInfoResponse? {
    val normalizedWord = word.trim().lowercase()
    val entity = wordInfoRepository.findByWord(normalizedWord) ?: return null
    return WordInfoResponse(
      word = entity.word,
      definition = entity.definition,
      synonyms = parseStringList(entity.synonyms),
      antonyms = parseStringList(entity.antonyms)
    )
  }

  private fun translateSafely(text: String): String =
    try {
      libreTranslateClient.translate(text)
    } catch (ex: Exception) {
      log.error("Failed to translate '{}': {}", text, ex.message, ex)
      ""
    }

  private fun generateFromApi(word: String, count: Int): List<String> {
    val prompt = PROMPT_TEMPLATE.format(count, word)
    val response = chadApiClient.ask(prompt)

    val rawText = response.response
      ?: throw IllegalStateException("Chad API returned empty response text")

    return parseSentences(rawText)
  }

  private fun parseSentences(rawText: String): List<String> {
    val jsonArray = extractJsonArray(rawText)
    return try {
      objectMapper.readValue<List<String>>(jsonArray)
    } catch (ex: Exception) {
      log.error("Failed to parse sentences from response: {}", rawText, ex)
      throw IllegalStateException("Failed to parse AI response as JSON array", ex)
    }
  }

  private fun extractJsonArray(text: String): String {
    val start = text.indexOf('[')
    val end = text.lastIndexOf(']')
    if (start == -1 || end == -1 || end <= start) {
      throw IllegalStateException("No JSON array found in AI response: $text")
    }
    return text.substring(start, end + 1)
  }

  private fun extractJsonObject(text: String): String {
    val start = text.indexOf('{')
    val end = text.lastIndexOf('}')
    if (start == -1 || end == -1 || end <= start) {
      throw IllegalStateException("No JSON object found in AI response: $text")
    }
    return text.substring(start, end + 1)
  }

  private fun parseStringList(json: String): List<String> =
    try {
      if (json.isBlank()) emptyList() else objectMapper.readValue<List<String>>(json)
    } catch (ex: Exception) {
      log.error("Failed to parse string list from '{}': {}", json, ex.message, ex)
      emptyList()
    }

  private fun buildSentenceResponse(word: String, wordRu: String, sentences: List<Sentence>) =
    SentenceResponse(
      word = word,
      wordRu = wordRu,
      sentences = sentences.map { TranslatedSentence(it.text, it.textRu) }
    )

  private fun <T> withWordLock(word: String, action: () -> T): T =
    wordLocks.computeIfAbsent(word) { ReentrantLock() }.withLock(action)

  private fun generateTranslateAndSaveSync(
    word: String,
    wordRu: String,
    count: Int
  ): List<TranslatedSentence> {
    if (count <= 0) return emptyList()

    val sentences = generateFromApi(word, count)
    val translations = libreTranslateClient.translateBatch(sentences)
    val pairs = sentences.mapIndexed { idx, en ->
      TranslatedSentence(en, translations.getOrNull(idx).orEmpty())
    }
    val entitiesCount = saveSentences(word, wordRu, pairs)
    log.info("Successfully saved {} sentence(s) for word '{}'", entitiesCount, word)
    return pairs
  }

  private fun scheduleBackgroundGeneration(word: String, wordRu: String, count: Int) {
    if (count <= 0 || !backgroundGenerationWords.add(word)) return

    backgroundScope.launch {
      try {
        withWordLock(word) {
          generateTranslateAndSave(word, wordRu, count)
        }
      } finally {
        backgroundGenerationWords.remove(word)
      }
    }
  }

  private fun generateTranslateAndSave(word: String, wordRu: String, count: Int) {
    try {
      val sentences = generateFromApi(word, count)
      val translations = libreTranslateClient.translateBatch(sentences)
      val pairs = sentences.mapIndexed { idx, en ->
        TranslatedSentence(en, translations.getOrNull(idx).orEmpty())
      }
      val savedCount = saveSentences(word, wordRu, pairs)
      log.info("Successfully generated {} and saved {} async sentence(s) for word '{}'", pairs.size, savedCount, word)
    } catch (ex: Exception) {
      log.error("Failed to generate/save async sentences for word '{}': {}", word, ex.message, ex)
    }
  }

  private fun saveSentences(word: String, wordRu: String, sentences: List<TranslatedSentence>): Int {
    if (sentences.isEmpty()) return 0

    return try {
      val uniqueSentences = sentences.distinctBy { it.text }
      val existingTexts = sentenceRepository.findExistingTexts(
        word = word,
        texts = uniqueSentences.map { it.text }
      ).toSet()
      val entities = uniqueSentences
        .filterNot { it.text in existingTexts }
        .map { Sentence(word = word, wordRu = wordRu, text = it.text, textRu = it.textRu) }

      if (entities.isEmpty()) {
        log.info("No new sentences to save for word '{}'", word)
        return 0
      }

      sentenceRepository.saveAll(entities)
      entities.size
    } catch (ex: Exception) {
      log.error("Failed to save sentences for word '{}': {}", word, ex.message, ex)
      0
    }
  }

  private fun generateAndSaveWordInfo(word: String) {
    try {
      if (wordInfoRepository.existsByWord(word)) {
        log.info("Word info for '{}' already exists, skipping LLM call", word)
        return
      }

      val prompt = WORD_INFO_PROMPT_TEMPLATE.format(word)
      val response = chadApiClient.ask(prompt)
      val rawText = response.response
        ?: throw IllegalStateException("Chad API returned empty response text for word info")

      val jsonObject = extractJsonObject(rawText)
      val parsed = objectMapper.readValue<Map<String, Any?>>(jsonObject)

      val definition = (parsed["definition"] as? String).orEmpty()

      @Suppress("UNCHECKED_CAST")
      val synonyms = (parsed["synonyms"] as? List<String>).orEmpty()

      @Suppress("UNCHECKED_CAST")
      val antonyms = (parsed["antonyms"] as? List<String>).orEmpty()

      val entity = WordInfo(
        word = word,
        definition = definition,
        synonyms = objectMapper.writeValueAsString(synonyms),
        antonyms = objectMapper.writeValueAsString(antonyms)
      )
      wordInfoRepository.save(entity)
      log.info("Successfully saved word info for '{}'", word)
    } catch (ex: Exception) {
      log.error("Failed to generate/save word info for '{}': {}", word, ex.message, ex)
    }
  }
}
