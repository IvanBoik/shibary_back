package org.boiko.shibary_back.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class LibreTranslateRequest(
    @JsonProperty("q") val q: Any,
    val source: String,
    val target: String,
    val format: String = "text",
    @JsonProperty("api_key") val apiKey: String? = null
)

data class LibreTranslateSingleResponse(
    val translatedText: String
)

data class LibreTranslateBatchResponse(
    val translatedText: List<String>
)
