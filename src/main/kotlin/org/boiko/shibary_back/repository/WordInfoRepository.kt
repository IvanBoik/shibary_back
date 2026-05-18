package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.WordInfo
import org.springframework.data.repository.CrudRepository

interface WordInfoRepository : CrudRepository<WordInfo, Long> {

  fun existsByWord(word: String): Boolean

  fun findByWord(word: String): WordInfo?
}
