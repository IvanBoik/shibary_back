package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.boiko.shibary_back.model.GoogleTokenInfo
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Service
class GoogleAuthService(
  private val properties: AuthProperties,
  builder: RestClient.Builder,
) {
  private val restClient: RestClient = builder.baseUrl(GOOGLE_TOKENINFO_URL).build()

  fun verify(idToken: String): GoogleTokenInfo {
    if (properties.googleWebClientId.isBlank()) {
      throw ApiException("INVALID_GOOGLE_TOKEN", "Google auth is not configured", HttpStatus.UNAUTHORIZED)
    }

    val response = runCatching {
      restClient.get()
        .uri { it.queryParam("id_token", idToken).build() }
        .retrieve()
        .body<GoogleTokenInfoResponse>()
    }.getOrElse {
      throw ApiException("INVALID_GOOGLE_TOKEN", "Invalid Google token", HttpStatus.UNAUTHORIZED)
    } ?: throw ApiException("INVALID_GOOGLE_TOKEN", "Invalid Google token", HttpStatus.UNAUTHORIZED)

    if (response.aud != properties.googleWebClientId || response.sub.isBlank() || response.iss !in TRUSTED_ISSUERS) {
      throw ApiException("INVALID_GOOGLE_TOKEN", "Invalid Google token", HttpStatus.UNAUTHORIZED)
    }

    return GoogleTokenInfo(
      sub = response.sub,
      email = response.email?.takeIf { it.isNotBlank() },
      emailVerified = response.emailVerified == true,
      name = response.name?.takeIf { it.isNotBlank() },
    )
  }

  data class GoogleTokenInfoResponse(
    val sub: String = "",
    val aud: String = "",
    val iss: String = "",
    val email: String? = null,
    val emailVerified: Boolean? = null,
    val name: String? = null,
  )

  companion object {
    private const val GOOGLE_TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo"
    private val TRUSTED_ISSUERS = setOf("accounts.google.com", "https://accounts.google.com")
  }
}
