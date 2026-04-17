package org.boiko.shibary_back.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class AppConfig {
    @Bean
    fun restClientBuilder() = RestClient.builder()
}