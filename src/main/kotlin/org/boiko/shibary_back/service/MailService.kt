package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.core.io.ClassPathResource
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
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

    runCatching {
      val mimeMessage = mailSender.createMimeMessage()
      // Multipart mode is required to set both plain-text and HTML alternatives.
      val helper = MimeMessageHelper(mimeMessage, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, CHARSET)
      helper.setFrom(properties.emailVerification.mailFrom)
      helper.setTo(toEmail)
      helper.setSubject(SUBJECT)
      // Provide both plain-text and HTML parts: clients that block HTML still get a readable code.
      helper.setText(buildPlainText(code, ttlMinutes), buildHtml(code, ttlMinutes))
      mailSender.send(mimeMessage)
    }
      .onFailure { log.error("Failed to send verification email to '{}': {}", toEmail, it.message) }
      .onSuccess { log.info("Verification email sent to '{}'", toEmail) }
  }

  private fun buildPlainText(code: String, ttlMinutes: Long): String = buildString {
    append("Your email confirmation code is: $code\n\n")
    append("It is valid for about $ttlMinutes minute(s).\n")
    append("If you did not request this, you can ignore this message.")
  }

  /**
   * Lightweight inline-styled HTML rendered from the [HTML_TEMPLATE_PATH] classpath resource.
   * Note: email clients strip <script> and JS event handlers, so a real "tap to copy" is not
   * possible. Instead the code is rendered as a large, selectable badge that is easy to
   * highlight / long-press-copy on both desktop and mobile.
   */
  private fun buildHtml(code: String, ttlMinutes: Long): String =
    htmlTemplate
      .replace(CODE_PLACEHOLDER, code)
      .replace(TTL_MINUTES_PLACEHOLDER, ttlMinutes.toString())

  /** Loaded once and cached: the template is immutable and shipped with the application. */
  private val htmlTemplate: String by lazy {
    ClassPathResource(HTML_TEMPLATE_PATH).inputStream.use { it.reader(Charsets.UTF_8).readText() }
  }

  companion object {
    private const val SECONDS_PER_MINUTE = 60
    private const val CHARSET = "UTF-8"
    private const val SUBJECT = "Your confirmation code"
    private const val HTML_TEMPLATE_PATH = "templates/mail/verification-code.html"
    private const val CODE_PLACEHOLDER = "{{code}}"
    private const val TTL_MINUTES_PLACEHOLDER = "{{ttlMinutes}}"
  }
}
