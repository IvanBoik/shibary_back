package org.boiko.shibary_back.dto

/**
 * User representation exposed by the admin panel. Unlike [UserDto] it also carries the
 * moderation state ([banned]) needed to manage accounts.
 */
data class AdminUserDto(
  val id: String,
  val email: String?,
  val displayName: String?,
  val emailVerified: Boolean,
  val banned: Boolean,
)

/** Payload to create a new password-based account from the admin panel. */
data class AdminCreateUserRequest(
  val email: String,
  val password: String,
  val displayName: String? = null,
)
