package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
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
      val helper = MimeMessageHelper(mimeMessage, false, CHARSET)
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
   * Lightweight inline-styled HTML. Note: email clients strip <script> and JS event handlers, so a
   * real "tap to copy" is not possible. Instead the code is rendered as a large, selectable badge
   * that is easy to highlight / long-press-copy on both desktop and mobile.
   */
  private fun buildHtml(code: String, ttlMinutes: Long): String = """
    <!DOCTYPE html>
    <html lang="en">
    <body style="margin:0;padding:0;background-color:#f5f6f8;">
      <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
             style="background-color:#f5f6f8;padding:32px 0;">
        <tr>
          <td align="center">
            <table role="presentation" width="100%" cellpadding="0" cellspacing="0"
                   style="max-width:440px;background-color:#ffffff;border-radius:16px;
                          border:1px solid #e2e5ea;padding:36px 32px;
                          font-family:-apple-system,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
              <tr>
                <td align="center" style="font-size:20px;font-weight:700;color:#111827;padding-bottom:8px;">
                  Confirm your email
                </td>
              </tr>
              <tr>
                <td align="center" style="font-size:14px;line-height:20px;color:#6b7280;padding-bottom:28px;">
                  Use the code below to finish signing in.
                </td>
              </tr>
              <tr>
                <td align="center">
                  <div style="display:inline-block;background-color:#f3f4f6;border:1px solid #e2e5ea;
                              border-radius:12px;padding:18px 28px;">
                    <span style="font-family:'SF Mono',ui-monospace,Menlo,Consolas,monospace;
                                 font-size:40px;font-weight:700;letter-spacing:10px;color:#2563eb;">
                      $code
                    </span>
                  </div>
                </td>
              </tr>
              <tr>
                <td align="center" style="font-size:13px;color:#6b7280;padding-top:24px;line-height:20px;">
                  The code is valid for about <strong style="color:#111827;">$ttlMinutes minute(s)</strong>.<br>
                  Tap and hold the code to copy it.
                </td>
              </tr>
              <tr>
                <td align="center" style="font-size:12px;color:#9ca3af;padding-top:24px;
                                          border-top:1px solid #e2e5ea;line-height:18px;">
                  If you did not request this, you can safely ignore this email.
                </td>
              </tr>
            </table>
          </td>
        </tr>
      </table>
    </body>
    </html>
  """.trimIndent()

  companion object {
    private const val SECONDS_PER_MINUTE = 60
    private const val CHARSET = "UTF-8"
    private const val SUBJECT = "Your confirmation code"
  }
}
