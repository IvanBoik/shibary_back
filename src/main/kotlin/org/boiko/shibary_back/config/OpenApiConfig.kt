package org.boiko.shibary_back.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.context.annotation.Configuration

/**
 * Central OpenAPI metadata + a reusable HTTP Bearer (JWT) security scheme named [BEARER_SCHEME].
 * Protected operations reference it via `@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)`.
 */
@OpenAPIDefinition(
  info = Info(
    title = "English Learning API",
    version = "1.0.0",
    description = "Optional auth (email/password & Google) and cross-device delta sync " +
      "for the Android app com.boiko.english_learning. Public /api/sentences endpoints require no auth.",
  ),
  servers = [
    Server(url = "/", description = "Current host"),
  ],
)
@SecurityScheme(
  name = OpenApiConfig.BEARER_SCHEME,
  type = SecuritySchemeType.HTTP,
  scheme = "bearer",
  bearerFormat = "JWT",
  description = "Paste the raw access token (without the 'Bearer ' prefix).",
)
@Configuration
class OpenApiConfig {
  companion object {
    const val BEARER_SCHEME = "bearerAuth"
  }
}
