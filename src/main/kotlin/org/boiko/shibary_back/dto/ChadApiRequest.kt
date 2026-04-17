package org.boiko.shibary_back.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ChadApiRequest(
    val message: String,
    @JsonProperty("api_key") val apiKey: String
)
