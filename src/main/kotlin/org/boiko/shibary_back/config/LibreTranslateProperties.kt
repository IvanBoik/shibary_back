package org.boiko.shibary_back.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "libretranslate")
data class LibreTranslateProperties(
    val url: String,
    val apiKey: String? = null,
    val sourceLang: String = "en",
    val targetLang: String = "ru"
)
