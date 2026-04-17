package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.Sentence
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository

interface SentenceRepository : CrudRepository<Sentence, Long> {

    fun existsByWord(word: String): Boolean

    fun countByWord(word: String): Long

    @Query("SELECT * FROM sentence WHERE word = :word ORDER BY id LIMIT :limit OFFSET :offset")
    fun findByWord(word: String, limit: Int, offset: Int): List<Sentence>
}
