package org.boiko.shibary_back.service

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.ParameterizedTypeReference
import org.springframework.stereotype.Service

@Service
class SentenceGenerationService(chatClientBuilder: ChatClient.Builder) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val chatClient: ChatClient = chatClientBuilder.build()

    companion object {
        private const val PROMPT_TEMPLATE =
            """Generate exactly {count} unique simple English sentence(s) (about 5 words) using the word "{word}".
Return ONLY a valid JSON array of strings with no extra text.
Example for count=2: ["I like apples.","She eats apples."]"""
    }

    fun generate(word: String, count: Int): List<String> {
        log.info("Generating {} sentence(s) for word '{}'", count, word)

        try {
            val result: List<String>? = chatClient.prompt()
                .user { it.text(PROMPT_TEMPLATE).param("count", count).param("word", word) }
                .call()
                .entity(object : ParameterizedTypeReference<List<String>>() {})

            log.debug("Generated result: {}", result)
            return result ?: emptyList()
        } catch (ex: Exception) {
            log.error("Failed to generate sentences for word '{}': {}", word, ex.message, ex)
            throw ex
        }
    }
}
