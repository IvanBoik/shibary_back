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

  companion object {
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

    if (sentenceRepository.existsByWord(normalizedWord)) {
      val sentences = findByWordWrapping(normalizedWord, count, offset ?: 0)
      val wordRu = sentences.firstOrNull()?.wordRu ?: translateSafely(normalizedWord)
      return SentenceResponse(
        word = normalizedWord,
        wordRu = wordRu,
        sentences = sentences.map { TranslatedSentence(it.text, it.textRu) }
      )
    }

    val totalCount = chadApiProperties.sentenceCount
    val syncCount = count % totalCount
    val asyncCount = totalCount - syncCount

    val syncSentences = generateFromApi(normalizedWord, syncCount)
    val syncTranslations = libreTranslateClient.translateBatch(syncSentences)
    val wordRu = translateSafely(normalizedWord)

    val syncPairs = syncSentences.mapIndexed { idx, en ->
      TranslatedSentence(en, syncTranslations.getOrNull(idx).orEmpty())
    }

    backgroundScope.launch {
      saveSentences(normalizedWord, wordRu, syncPairs)

      supervisorScope {
        launch { generateAndSaveWordInfo(normalizedWord) }
        if (asyncCount > 0) {
          launch { generateTranslateAndSave(normalizedWord, wordRu, asyncCount) }
        }
      }
    }

    return SentenceResponse(
      word = normalizedWord,
      wordRu = wordRu,
      sentences = syncPairs
    )
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

  private fun findByWordWrapping(word: String, count: Int, offset: Int): List<Sentence> {
    val total = sentenceRepository.countByWord(word).toInt()
    if (total == 0) return emptyList()

    val wrappedOffset = offset % total
    val tail = sentenceRepository.findByWord(word, count, wrappedOffset)

    if (tail.size >= count) return tail

    val remaining = count - tail.size
    val head = sentenceRepository.findByWord(word, remaining, 0)
    return tail + head
  }

  private fun generateTranslateAndSave(word: String, wordRu: String, count: Int) {
    try {
      val sentences = generateFromApi(word, count)
      val translations = libreTranslateClient.translateBatch(sentences)
      val pairs = sentences.mapIndexed { idx, en ->
        TranslatedSentence(en, translations.getOrNull(idx).orEmpty())
      }
      val entities = pairs.map {
        Sentence(word = word, wordRu = wordRu, text = it.text, textRu = it.textRu)
      }
      sentenceRepository.saveAll(entities)
      log.info("Successfully generated and saved {} async sentence(s) for word '{}'", entities.size, word)
    } catch (ex: Exception) {
      log.error("Failed to generate/save async sentences for word '{}': {}", word, ex.message, ex)
    }
  }

  private fun saveSentences(word: String, wordRu: String, sentences: List<TranslatedSentence>) {
    try {
      val entities = sentences.map {
        Sentence(word = word, wordRu = wordRu, text = it.text, textRu = it.textRu)
      }
      sentenceRepository.saveAll(entities)
      log.info("Successfully saved {} sentence(s) for word '{}'", sentences.size, word)
    } catch (ex: Exception) {
      log.error("Failed to save sentences for word '{}': {}", word, ex.message, ex)
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
