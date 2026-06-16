package org.boiko.shibary_back.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "auth")
data class AuthProperties(
  val jwtSecret: String = "dev-only-change-me-please-use-env-secret-with-32-chars",
  val issuer: String = "shibary-back",
  val audience: String = "shibary-mobile",
  val accessTtlSeconds: Long = 900,
  val refreshTtlDays: Long = 30,
  val googleWebClientId: String = "",
)
