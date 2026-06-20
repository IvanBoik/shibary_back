package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Service

/**
 * Sends transactional emails. The [JavaMailSender] bean only exists when SMTP is configured
 * (spring.mail.host). When it is absent the code is just logged, which keeps local development
 * and tests working without a mail server.
 */
@Service
class MailService(
  private val mailSenderProvider: ObjectProvider<JavaMailSender>,
  private val properties: AuthProperties,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun sendVerificationCode(toEmail: String, code: String) {
    val ttlMinutes = (properties.emailVerification.codeTtlSeconds / SECONDS_PER_MINUTE).coerceAtLeast(1)
    val mailSender = mailSenderProvider.getIfAvailable()
    if (mailSender == null) {
      log.warn("SMTP is not configured; confirmation code for '{}' is logged only: {}", toEmail, code)
      return
    }
    val message = SimpleMailMessage().apply {
      from = properties.emailVerification.mailFrom
      setTo(toEmail)
      subject = "Your confirmation code"
      text = buildString {
        append("Your email confirmation code is: $code\n\n")
        append("It is valid for about $ttlMinutes minute(s). ")
        append("If you did not request this, you can ignore this message.")
      }
    }
    runCatching { mailSender.send(message) }
      .onFailure { log.error("Failed to send verification email to '{}': {}", toEmail, it.message) }
      .onSuccess { log.info("Verification email sent to '{}'", toEmail) }
  }

  companion object {
    private const val SECONDS_PER_MINUTE = 60
  }
}
