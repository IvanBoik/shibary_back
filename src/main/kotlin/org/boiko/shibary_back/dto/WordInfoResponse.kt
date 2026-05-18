package org.boiko.shibary_back.dto

data class WordInfoResponse(
  val word: String,
  val definition: String,
  val synonyms: List<String>,
  val antonyms: List<String>
)
