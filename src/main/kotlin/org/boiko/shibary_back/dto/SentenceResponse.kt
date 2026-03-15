package org.boiko.shibary_back.dto

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class SentenceResponse @JsonCreator constructor(
    @JsonProperty("sentence") val sentence: String,
    @JsonProperty("translation") val translation: String
)
