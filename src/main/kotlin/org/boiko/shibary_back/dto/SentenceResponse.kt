package org.boiko.shibary_back.dto

data class SentenceResponse(
    val word: String,
    val sentences: List<String>
)
