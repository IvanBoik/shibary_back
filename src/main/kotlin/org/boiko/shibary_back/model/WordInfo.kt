package org.boiko.shibary_back.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("word_info")
data class WordInfo(
  @Id val id: Long? = null,
  val word: String,
  val definition: String,
  val synonyms: String,
  val antonyms: String,
  val createdAt: Instant = Instant.now()
)
