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

/**
 * One page of users plus the metadata the UI needs to render offset-based navigation
 * (page numbers and total count).
 */
data class AdminUserPageDto(
  val items: List<AdminUserDto>,
  val page: Int,
  val size: Int,
  val totalItems: Long,
  val totalPages: Int,
)

/** Payload to create a new password-based account from the admin panel. */
data class AdminCreateUserRequest(
  val email: String,
  val password: String,
  val displayName: String? = null,
)
