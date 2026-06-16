package org.boiko.shibary_back.model

import java.time.Instant
import java.util.UUID

data class AppUser(
  val id: UUID,
  val email: String?,
  val passwordHash: String?,
  val displayName: String?,
  val emailVerified: Boolean,
)

data class StoredRefreshToken(
  val id: UUID,
  val userId: UUID,
  val tokenHash: String,
  val expiresAt: Instant,
  val revoked: Boolean,
)

data class GoogleTokenInfo(
  val sub: String,
  val email: String?,
  val emailVerified: Boolean,
  val name: String?,
)
