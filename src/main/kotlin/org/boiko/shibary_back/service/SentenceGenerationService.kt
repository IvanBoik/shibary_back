package org.boiko.shibary_back.service

import org.boiko.shibary_back.dto.SentenceResponse
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
For each sentence, also provide its Russian translation.
Return ONLY a valid JSON array with no extra text. Each element must have exactly two fields: "sentence" (English) and "translation" (Russian).
Example for count=1: [{{"sentence":"I like apples.","translation":"Я люблю яблоки."}}]"""
    }

    fun generate(word: String, count: Int): List<SentenceResponse> {
        log.info("Generating {} sentence(s) for word '{}'", count, word)

        val result: List<SentenceResponse>? = chatClient.prompt()
            .user { it.text(PROMPT_TEMPLATE).param("count", count).param("word", word) }
            .call()
            .entity(object : ParameterizedTypeReference<List<SentenceResponse>>() {})

        log.debug("Generated result: {}", result)

        return result ?: emptyList()
    }
}
