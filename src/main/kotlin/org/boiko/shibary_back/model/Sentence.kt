package org.boiko.shibary_back.model

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("sentence")
data class Sentence(
    @Id val id: Long? = null,
    val word: String,
    val text: String,
    val createdAt: Instant = Instant.now()
)
