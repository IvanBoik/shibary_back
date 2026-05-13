package org.boiko.shibary_back.dto

data class SentenceResponse(
    val word: String,
    val wordRu: String,
    val sentences: List<TranslatedSentence>
)

data class TranslatedSentence(
    val text: String,
    val textRu: String
)
