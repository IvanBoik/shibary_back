package org.boiko.shibary_back.service

import org.boiko.shibary_back.client.ChadApiClient
import org.boiko.shibary_back.config.ChadApiProperties
import org.boiko.shibary_back.dto.SentenceResponse
import org.boiko.shibary_back.model.Sentence
import org.boiko.shibary_back.repository.SentenceRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue

@Service
class SentenceGenerationService(
    private val sentenceRepository: SentenceRepository,
    private val chadApiClient: ChadApiClient,
    private val chadApiProperties: ChadApiProperties,
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PROMPT_TEMPLATE =
            """Generate exactly %d unique simple English sentences (about 5 words each) using the word "%s".
               Return ONLY a valid JSON array of strings with no extra text.
               Each sentence must contain the given word.
               Example for word "apples": ["I like apples.","She eats apples."]"""
    }

    fun getSentences(word: String, count: Int, offset: Int): SentenceResponse {
        val normalizedWord = word.trim().lowercase()

        if (sentenceRepository.existsByWord(normalizedWord)) {
            val sentences = findByWordWrapping(normalizedWord, count, offset)
            return SentenceResponse(
                word = normalizedWord,
                sentences = sentences.map { it.text }
            )
        }

        val generatedSentences = generateFromApi(normalizedWord)

        saveAsync(normalizedWord, generatedSentences)

        val result = generatedSentences.take(count)
        return SentenceResponse(
            word = normalizedWord,
            sentences = result
        )
    }

    private fun generateFromApi(word: String): List<String> {
        val prompt = PROMPT_TEMPLATE.format(chadApiProperties.sentenceCount, word)
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

    /** Extracts the first JSON array from the response text (handles markdown code blocks). */
    private fun extractJsonArray(text: String): String {
        val start = text.indexOf('[')
        val end = text.lastIndexOf(']')
        if (start == -1 || end == -1 || end <= start) {
            throw IllegalStateException("No JSON array found in AI response: $text")
        }
        return text.substring(start, end + 1)
    }

    /**
     * Fetches [count] sentences for [word] starting at [offset], wrapping around to the beginning
     * when the end of available records is reached.
     */
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

    @Async
    fun saveAsync(word: String, sentences: List<String>) {
        try {
            val entities = sentences.map { Sentence(word = word, text = it) }
            sentenceRepository.saveAll(entities)
            log.info("Successfully saved {} sentence(s) for word '{}'", sentences.size, word)
        } catch (ex: Exception) {
            log.error("Failed to save sentences for word '{}': {}", word, ex.message, ex)
        }
    }
}
