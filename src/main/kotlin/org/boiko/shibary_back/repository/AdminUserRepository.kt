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

  fun listUsers(): List<AppUser> = jdbc.query(
    """
      SELECT id, email, password_hash, display_name, email_verified, banned
      FROM users
      ORDER BY created_at DESC
    """.trimIndent(),
  ) { rs, _ -> rs.toAppUser() }

  /** Returns the number of affected rows (0 when the user does not exist). */
  fun setBanned(userId: UUID, banned: Boolean): Int = jdbc.update(
    """
      UPDATE users
      SET banned = :banned, updated_at = NOW()
      WHERE id = :id
    """.trimIndent(),
    mapOf("id" to userId, "banned" to banned),
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
