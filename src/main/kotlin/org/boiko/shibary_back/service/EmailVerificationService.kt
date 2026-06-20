package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.boiko.shibary_back.dto.EmailVerificationChallengeDto
import org.boiko.shibary_back.model.AppUser
import org.boiko.shibary_back.repository.AuthRepository
import org.boiko.shibary_back.repository.EmailVerificationRepository
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

/**
 * Owns the lifecycle of the email confirmation code: issuing, resending (with cooldown),
 * and verifying. Codes are short-lived and only their hash is stored.
 */
@Service
class EmailVerificationService(
  private val authRepository: AuthRepository,
  private val verificationRepository: EmailVerificationRepository,
  private val mailService: MailService,
  private val properties: AuthProperties,
) {

  private val log = LoggerFactory.getLogger(javaClass)
  private val config get() = properties.emailVerification

  /** Issues and emails a fresh code for a just-registered user. Safe to call within registration. */
  @Transactional
  fun startVerification(user: AppUser): EmailVerificationChallengeDto {
    val email = user.email
      ?: throw ApiException("VALIDATION_ERROR", "User has no email to verify", HttpStatus.UNPROCESSABLE_ENTITY)
    val code = generateCode()
    val now = Instant.now()
    verificationRepository.upsert(
      userId = user.id,
      codeHash = hash(code),
      expiresAt = now.plusSeconds(config.codeTtlSeconds),
      lastSentAt = now,
    )
    mailService.sendVerificationCode(email, code)
    return EmailVerificationChallengeDto(
      emailVerified = false,
      expiresInSeconds = config.codeTtlSeconds,
      resendAvailableInSeconds = config.resendCooldownSeconds,
    )
  }

  /** Re-issues a code on user request, enforcing a cooldown between sends. */
  @Transactional
  fun resend(userId: UUID): EmailVerificationChallengeDto {
    val user = authRepository.findUserById(userId)
      ?: throw ApiException("INVALID_TOKEN", "User not found", HttpStatus.UNAUTHORIZED)
    if (user.emailVerified) {
      throw ApiException("EMAIL_ALREADY_VERIFIED", "Email is already verified", HttpStatus.CONFLICT)
    }

    val existing = verificationRepository.find(userId)
    if (existing != null) {
      val secondsSinceLastSend = ChronoUnit.SECONDS.between(existing.lastSentAt, Instant.now())
      val remaining = config.resendCooldownSeconds - secondsSinceLastSend
      if (remaining > 0) {
        throw ApiException("RATE_LIMITED", "Please wait before requesting a new code", HttpStatus.TOO_MANY_REQUESTS)
      }
    }
    return startVerification(user)
  }

  /** Validates the submitted code; on success marks the email verified and clears the challenge. */
  @Transactional
  fun verify(userId: UUID, rawCode: String): EmailVerificationChallengeDto {
    val user = authRepository.findUserById(userId)
      ?: throw ApiException("INVALID_TOKEN", "User not found", HttpStatus.UNAUTHORIZED)
    if (user.emailVerified) {
      return verifiedChallenge()
    }

    val challenge = verificationRepository.find(userId)
      ?: throw ApiException("CODE_NOT_FOUND", "No active confirmation code, request a new one", HttpStatus.UNPROCESSABLE_ENTITY)

    if (Instant.now().isAfter(challenge.expiresAt)) {
      verificationRepository.delete(userId)
      throw ApiException("CODE_EXPIRED", "Confirmation code has expired, request a new one", HttpStatus.UNPROCESSABLE_ENTITY)
    }
    if (challenge.attempts >= config.maxAttempts) {
      verificationRepository.delete(userId)
      throw ApiException("TOO_MANY_ATTEMPTS", "Too many attempts, request a new code", HttpStatus.TOO_MANY_REQUESTS)
    }

    if (!constantTimeEquals(hash(rawCode.trim()), challenge.codeHash)) {
      verificationRepository.incrementAttempts(userId)
      log.warn("Invalid email confirmation code for user '{}'", userId)
      throw ApiException("INVALID_CODE", "Confirmation code is incorrect", HttpStatus.UNPROCESSABLE_ENTITY)
    }

    authRepository.markEmailVerified(userId)
    verificationRepository.delete(userId)
    log.info("Email verified for user '{}'", userId)
    return verifiedChallenge()
  }

  private fun verifiedChallenge() = EmailVerificationChallengeDto(
    emailVerified = true,
    expiresInSeconds = 0,
    resendAvailableInSeconds = 0,
  )

  private fun generateCode(): String {
    val bound = TEN.pow(config.codeLength)
    val number = secureRandom.nextInt(bound)
    return number.toString().padStart(config.codeLength, '0')
  }

  private fun hash(code: String): String {
    val digest = MessageDigest.getInstance(SHA_256).digest(code.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(digest)
  }

  private fun constantTimeEquals(a: String, b: String): Boolean =
    MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

  private fun Int.pow(exp: Int): Int {
    var result = 1
    repeat(exp) { result *= this }
    return result
  }

  companion object {
    private const val SHA_256 = "SHA-256"
    private const val TEN = 10
    private val secureRandom = SecureRandom()
  }
}
