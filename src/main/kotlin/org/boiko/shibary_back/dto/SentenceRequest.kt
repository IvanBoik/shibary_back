package org.boiko.shibary_back.dto

data class SentenceRequest(
    val word: String = "",
    val count: Int = 1,
    val offset: Int = 0
)
