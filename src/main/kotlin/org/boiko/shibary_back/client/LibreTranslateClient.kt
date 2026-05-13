package org.boiko.shibary_back.client

import org.boiko.shibary_back.config.LibreTranslateProperties
import org.boiko.shibary_back.dto.LibreTranslateBatchResponse
import org.boiko.shibary_back.dto.LibreTranslateRequest
import org.boiko.shibary_back.dto.LibreTranslateSingleResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class LibreTranslateClient(
    private val properties: LibreTranslateProperties,
    restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = restClientBuilder.baseUrl(properties.url).build()

    fun translate(text: String): String {
        val request = LibreTranslateRequest(
            q = text,
            source = properties.sourceLang,
            target = properties.targetLang,
            apiKey = properties.apiKey
        )

        val response = restClient.post()
            .uri("/translate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<LibreTranslateSingleResponse>()
            ?: throw IllegalStateException("LibreTranslate returned null response")

        return response.translatedText
    }

    fun translateBatch(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()

        val request = LibreTranslateRequest(
            q = texts,
            source = properties.sourceLang,
            target = properties.targetLang,
            apiKey = properties.apiKey
        )

        val response = restClient.post()
            .uri("/translate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body<LibreTranslateBatchResponse>()
            ?: throw IllegalStateException("LibreTranslate returned null response")

        if (response.translatedText.size != texts.size) {
            log.warn(
                "LibreTranslate returned {} translations for {} input texts",
                response.translatedText.size, texts.size
            )
        }

        return response.translatedText
    }
}
