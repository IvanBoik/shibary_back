package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.EmailVerification
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Repository
class EmailVerificationRepository(private val jdbc: NamedParameterJdbcTemplate) {

  fun find(userId: UUID): EmailVerification? = jdbc.query(
    """
      SELECT user_id, code_hash, expires_at, attempts, last_sent_at
      FROM email_verifications
      WHERE user_id = :userId
    """.trimIndent(),
    mapOf("userId" to userId),
  ) { rs, _ ->
    EmailVerification(
      userId = rs.getObject("user_id", UUID::class.java),
      codeHash = rs.getString("code_hash"),
      expiresAt = rs.getTimestamp("expires_at").toInstant(),
      attempts = rs.getInt("attempts"),
      lastSentAt = rs.getTimestamp("last_sent_at").toInstant(),
    )
  }.firstOrNull()

  /** Inserts or replaces the pending verification challenge for a user (resets attempts). */
  fun upsert(userId: UUID, codeHash: String, expiresAt: Instant, lastSentAt: Instant) {
    jdbc.update(
      """
        INSERT INTO email_verifications (user_id, code_hash, expires_at, attempts, last_sent_at)
        VALUES (:userId, :codeHash, :expiresAt, 0, :lastSentAt)
        ON CONFLICT (user_id) DO UPDATE SET
          code_hash = EXCLUDED.code_hash,
          expires_at = EXCLUDED.expires_at,
          attempts = 0,
          last_sent_at = EXCLUDED.last_sent_at
      """.trimIndent(),
      mapOf(
        "userId" to userId,
        "codeHash" to codeHash,
        "expiresAt" to Timestamp.from(expiresAt),
        "lastSentAt" to Timestamp.from(lastSentAt),
      ),
    )
  }

  fun incrementAttempts(userId: UUID) {
    jdbc.update(
      "UPDATE email_verifications SET attempts = attempts + 1 WHERE user_id = :userId",
      mapOf("userId" to userId),
    )
  }

  fun delete(userId: UUID) {
    jdbc.update("DELETE FROM email_verifications WHERE user_id = :userId", mapOf("userId" to userId))
  }
}
