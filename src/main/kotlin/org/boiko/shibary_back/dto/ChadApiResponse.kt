package org.boiko.shibary_back.dto

import com.fasterxml.jackson.annotation.JsonProperty

data class ChadApiResponse(
    @JsonProperty("is_success") val isSuccess: Boolean,
    val response: String? = null,
    @JsonProperty("used_words_count") val usedWordsCount: Int? = null,
    @JsonProperty("error_code") val errorCode: String? = null,
    @JsonProperty("error_message") val errorMessage: String? = null
)
