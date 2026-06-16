package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.boiko.shibary_back.model.AppUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@Service
class JwtService(private val properties: AuthProperties) {

  fun createAccessToken(user: AppUser): String {
    val now = Instant.now().epochSecond
    val expiresAt = now + properties.accessTtlSeconds
    val header = mapOf("alg" to ALGORITHM, "typ" to TOKEN_TYPE)
    val payload = mapOf(
      "sub" to user.id.toString(),
      "email" to user.email,
      "iss" to properties.issuer,
      "aud" to properties.audience,
      "iat" to now,
      "exp" to expiresAt,
    )
    return sign(header.toJsonObject(), payload.toJsonObject())
  }

  fun validateAccessToken(token: String): UUID {
    try {
      val parts = token.split(PART_SEPARATOR)
      if (parts.size != JWT_PARTS) invalidToken()

      val expectedSignature = hmacSha256("${parts[0]}.${parts[1]}")
      if (!constantTimeEquals(expectedSignature, parts[2])) invalidToken()

      val payloadJson = String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
      val subject = extractStringClaim(payloadJson, "sub") ?: invalidToken()
      val issuer = extractStringClaim(payloadJson, "iss") ?: invalidToken()
      val audience = extractStringClaim(payloadJson, "aud") ?: invalidToken()
      val expiresAt = extractLongClaim(payloadJson, "exp") ?: invalidToken()

      if (issuer != properties.issuer || audience != properties.audience) invalidToken()
      if (Instant.now().epochSecond >= expiresAt) {
        throw ApiException("TOKEN_EXPIRED", "Access token has expired", HttpStatus.UNAUTHORIZED)
      }
      return UUID.fromString(subject)
    } catch (ex: ApiException) {
      throw ex
    } catch (_: Exception) {
      invalidToken()
    }
  }

  private fun sign(headerJson: String, payloadJson: String): String {
    val header = base64Url(headerJson)
    val payload = base64Url(payloadJson)
    val signature = hmacSha256("$header.$payload")
    return "$header.$payload.$signature"
  }

  private fun hmacSha256(value: String): String {
    val mac = Mac.getInstance(HMAC_SHA256)
    mac.init(SecretKeySpec(properties.jwtSecret.toByteArray(StandardCharsets.UTF_8), HMAC_SHA256))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.toByteArray(StandardCharsets.UTF_8)))
  }

  private fun base64Url(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

  private fun Map<String, Any?>.toJsonObject(): String = entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
    val jsonValue = when (value) {
      null -> "null"
      is Number, is Boolean -> value.toString()
      else -> "\"${value.toString().replace("\\", "\\\\").replace("\"", "\\\"")}\""
    }
    "\"$key\":$jsonValue"
  }

  private fun extractStringClaim(json: String, name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)

  private fun extractLongClaim(json: String, name: String): Long? =
    Regex("\"$name\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()

  private fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(left.toByteArray(StandardCharsets.UTF_8), right.toByteArray(StandardCharsets.UTF_8))

  private fun invalidToken(): Nothing = throw ApiException("INVALID_TOKEN", "Invalid token", HttpStatus.UNAUTHORIZED)

  companion object {
    private const val ALGORITHM = "HS256"
    private const val TOKEN_TYPE = "JWT"
    private const val HMAC_SHA256 = "HmacSHA256"
    private const val JWT_PARTS = 3
    private const val PART_SEPARATOR = "."
  }
}
