package org.boiko.shibary_back.dto

data class RegisterRequest(
  val email: String,
  val password: String,
  val displayName: String? = null,
)

data class LoginRequest(
  val email: String,
  val password: String,
)

data class GoogleAuthRequest(val idToken: String)

data class RefreshRequest(val refreshToken: String)

data class LogoutRequest(val refreshToken: String)

data class UserDto(
  val id: String,
  val email: String?,
  val displayName: String?,
  val emailVerified: Boolean,
)

data class AuthResponse(
  val user: UserDto,
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
)

data class RefreshResponse(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
)
