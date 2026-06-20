package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.AppUser
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * Read/maintenance queries used exclusively by the admin panel.
 *
 * Only active outside the `admin` profile: the standalone Spring Boot Admin container has no
 * datasource (see `application-admin.yaml`), so the user database lives next to the main app.
 */
@Repository
@Profile("!admin")
class AdminUserRepository(private val jdbc: NamedParameterJdbcTemplate) {

  /** Returns a single page of users ordered by newest first. */
  fun listUsers(limit: Int, offset: Int): List<AppUser> = jdbc.query(
    """
      SELECT id, email, password_hash, display_name, email_verified, banned
      FROM users
      ORDER BY created_at DESC, id DESC
      LIMIT :limit OFFSET :offset
    """.trimIndent(),
    mapOf("limit" to limit, "offset" to offset),
  ) { rs, _ -> rs.toAppUser() }

  /** Total number of users, used to compute the page count. */
  fun countUsers(): Long = jdbc.queryForObject(
    "SELECT COUNT(*) FROM users",
    emptyMap<String, Any>(),
    Long::class.java,
  ) ?: 0L

  /** Returns the number of affected rows (0 when the user does not exist). */
  fun setBanned(userId: UUID, banned: Boolean): Int = jdbc.update(
    """
      UPDATE users
      SET banned = :banned, updated_at = NOW()
      WHERE id = :id
    """.trimIndent(),
    mapOf("id" to userId, "banned" to banned),
  )

  /** Marks the email as verified. Returns the number of affected rows (0 when the user does not exist). */
  fun setEmailVerified(userId: UUID): Int = jdbc.update(
    """
      UPDATE users
      SET email_verified = true, updated_at = NOW()
      WHERE id = :id
    """.trimIndent(),
    mapOf("id" to userId),
  )

  /** Returns the number of affected rows (0 when the user does not exist). */
  fun deleteUser(userId: UUID): Int = jdbc.update(
    "DELETE FROM users WHERE id = :id",
    mapOf("id" to userId),
  )

  private fun ResultSet.toAppUser(): AppUser = AppUser(
    id = getObject("id", UUID::class.java),
    email = getString("email"),
    passwordHash = getString("password_hash"),
    displayName = getString("display_name"),
    emailVerified = getBoolean("email_verified"),
    banned = getBoolean("banned"),
  )
}
