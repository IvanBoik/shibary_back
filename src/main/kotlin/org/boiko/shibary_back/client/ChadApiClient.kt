package org.boiko.shibary_back.client

import org.boiko.shibary_back.config.ChadApiProperties
import org.boiko.shibary_back.dto.ChadApiRequest
import org.boiko.shibary_back.dto.ChadApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ChadApiClient(
    private val properties: ChadApiProperties,
    restClientBuilder: RestClient.Builder
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restClient: RestClient = restClientBuilder.baseUrl(properties.url).build()

    fun ask(prompt: String): ChadApiResponse {
        val request = ChadApiRequest(message = prompt, apiKey = properties.key)

        val response = restClient.post()
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(ChadApiResponse::class.java)
            ?: throw IllegalStateException("Chad API returned null response")

        if (!response.isSuccess) {
            val msg = "Chad API error [${response.errorCode}]: ${response.errorMessage}"
            log.error(msg)
            throw RuntimeException(msg)
        }

        return response
    }
}
