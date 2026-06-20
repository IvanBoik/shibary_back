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
  val emailVerification: EmailVerificationProperties = EmailVerificationProperties(),
)

data class EmailVerificationProperties(
  /** How long (seconds) a freshly issued confirmation code stays valid. */
  val codeTtlSeconds: Long = 60,
  /** Minimum delay (seconds) between two consecutive code requests for the same user. */
  val resendCooldownSeconds: Long = 30,
  /** Number of allowed wrong attempts before the code is invalidated and a resend is required. */
  val maxAttempts: Int = 5,
  /** Number of digits in the confirmation code. */
  val codeLength: Int = 6,
  /** "From" address used when sending confirmation emails. */
  val mailFrom: String = "no-reply@shibary.app",
)
