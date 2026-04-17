package org.boiko.shibary_back.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "chad.api")
data class ChadApiProperties(
    val key: String,
    val url: String,
    val sentenceCount: Int = 10
)
