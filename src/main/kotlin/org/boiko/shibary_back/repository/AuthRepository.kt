package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.AppUser
import org.boiko.shibary_back.model.GoogleTokenInfo
import org.boiko.shibary_back.model.StoredRefreshToken
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Repository
class AuthRepository(private val jdbc: NamedParameterJdbcTemplate) {

  fun findUserByEmail(email: String): AppUser? = jdbc.query(
    """
      SELECT id, email, password_hash, display_name, email_verified, banned
      FROM users
      WHERE email = :email
    """.trimIndent(),
    mapOf("email" to normalizeEmail(email)),
  ) { rs, _ -> rs.toAppUser() }.firstOrNull()

  fun findUserById(userId: UUID): AppUser? = jdbc.query(
    """
      SELECT id, email, password_hash, display_name, email_verified, banned
      FROM users
      WHERE id = :id
    """.trimIndent(),
    mapOf("id" to userId),
  ) { rs, _ -> rs.toAppUser() }.firstOrNull()

  fun createPasswordUser(email: String, passwordHash: String, displayName: String?): AppUser {
    val userId = UUID.randomUUID()
    jdbc.update(
      """
        INSERT INTO users (id, email, password_hash, display_name, email_verified)
        VALUES (:id, :email, :passwordHash, :displayName, false)
      """.trimIndent(),
      mapOf(
        "id" to userId,
        "email" to normalizeEmail(email),
        "passwordHash" to passwordHash,
        "displayName" to displayName,
      ),
    )
    return findUserById(userId) ?: error("Created user was not found")
  }

  fun findUserByOAuth(provider: String, providerUserId: String): AppUser? = jdbc.query(
    """
      SELECT u.id, u.email, u.password_hash, u.display_name, u.email_verified, u.banned
      FROM users u
      JOIN oauth_accounts oa ON oa.user_id = u.id
      WHERE oa.provider = :provider AND oa.provider_user_id = :providerUserId
    """.trimIndent(),
    mapOf("provider" to provider, "providerUserId" to providerUserId),
  ) { rs, _ -> rs.toAppUser() }.firstOrNull()

  fun createGoogleUser(tokenInfo: GoogleTokenInfo): AppUser {
    val userId = UUID.randomUUID()
    jdbc.update(
      """
        INSERT INTO users (id, email, password_hash, display_name, email_verified)
        VALUES (:id, :email, null, :displayName, :emailVerified)
      """.trimIndent(),
      mapOf(
        "id" to userId,
        "email" to tokenInfo.email?.let(::normalizeEmail),
        "displayName" to tokenInfo.name,
        "emailVerified" to tokenInfo.emailVerified,
      ),
    )
    jdbc.update(
      """
        INSERT INTO oauth_accounts (id, user_id, provider, provider_user_id)
        VALUES (:id, :userId, :provider, :providerUserId)
      """.trimIndent(),
      mapOf(
        "id" to UUID.randomUUID(),
        "userId" to userId,
        "provider" to GOOGLE_PROVIDER,
        "providerUserId" to tokenInfo.sub,
      ),
    )
    return findUserById(userId) ?: error("Created Google user was not found")
  }

  fun storeRefreshToken(userId: UUID, tokenHash: String, expiresAt: Instant) {
    jdbc.update(
      """
        INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at, revoked)
        VALUES (:id, :userId, :tokenHash, :expiresAt, false)
      """.trimIndent(),
      mapOf("id" to UUID.randomUUID(), "userId" to userId, "tokenHash" to tokenHash, "expiresAt" to Timestamp.from(expiresAt)),
    )
  }

  fun findRefreshToken(tokenHash: String): StoredRefreshToken? = jdbc.query(
    """
      SELECT id, user_id, token_hash, expires_at, revoked
      FROM refresh_tokens
      WHERE token_hash = :tokenHash
    """.trimIndent(),
    mapOf("tokenHash" to tokenHash),
  ) { rs, _ ->
    StoredRefreshToken(
      id = rs.getObject("id", UUID::class.java),
      userId = rs.getObject("user_id", UUID::class.java),
      tokenHash = rs.getString("token_hash"),
      expiresAt = rs.getTimestamp("expires_at").toInstant(),
      revoked = rs.getBoolean("revoked"),
    )
  }.firstOrNull()

  fun revokeRefreshToken(tokenHash: String) {
    jdbc.update(
      "UPDATE refresh_tokens SET revoked = true WHERE token_hash = :tokenHash",
      mapOf("tokenHash" to tokenHash),
    )
  }

  fun revokeAllRefreshTokensForUser(userId: UUID) {
    jdbc.update(
      "UPDATE refresh_tokens SET revoked = true WHERE user_id = :userId",
      mapOf("userId" to userId),
    )
  }

  private fun ResultSet.toAppUser(): AppUser = AppUser(
    id = getObject("id", UUID::class.java),
    email = getString("email"),
    passwordHash = getString("password_hash"),
    displayName = getString("display_name"),
    emailVerified = getBoolean("email_verified"),
    banned = getBoolean("banned"),
  )

  private fun normalizeEmail(email: String): String = email.trim().lowercase()

  companion object {
    const val GOOGLE_PROVIDER = "google"
  }
}
