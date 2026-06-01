package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.Sentence
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface SentenceRepository : CrudRepository<Sentence, Long> {

  fun countByWord(word: String): Long

  @Query("SELECT * FROM sentence WHERE word = :word ORDER BY id LIMIT :limit OFFSET :offset")
  fun findByWord(word: String, limit: Int, offset: Int): List<Sentence>

  @Query("SELECT text FROM sentence WHERE word = :word AND text IN (:texts)")
  fun findExistingTexts(word: String, texts: Collection<String>): List<String>

  @Query("SELECT * FROM sentence WHERE word = :word ORDER BY id")
  fun findAllByWordOrdered(word: String): List<Sentence>

  @Query("SELECT text FROM sentence WHERE word = :word ORDER BY id")
  fun findTextsByWord(word: String): List<String>
}
