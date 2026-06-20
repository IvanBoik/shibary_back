package org.boiko.shibary_back.dto

data class VerifyEmailRequest(val code: String)

/**
 * Returned by register / resend so the client knows how long the code is valid
 * and when another resend will be allowed.
 */
data class EmailVerificationChallengeDto(
  val emailVerified: Boolean,
  val expiresInSeconds: Long,
  val resendAvailableInSeconds: Long,
)
