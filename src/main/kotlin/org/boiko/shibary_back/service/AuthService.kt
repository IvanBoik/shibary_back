package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.boiko.shibary_back.dto.AuthResponse
import org.boiko.shibary_back.dto.RefreshResponse
import org.boiko.shibary_back.dto.UserDto
import org.boiko.shibary_back.model.AppUser
import org.boiko.shibary_back.repository.AuthRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID

@Service
class AuthService(
  private val authRepository: AuthRepository,
  private val passwordEncoder: PasswordEncoder,
  private val jwtService: JwtService,
  private val googleAuthService: GoogleAuthService,
  private val properties: AuthProperties,
) {

  @Transactional
  fun register(email: String, password: String, displayName: String?): AuthResponse {
    validateEmailAndPassword(email, password)
    if (authRepository.findUserByEmail(email) != null) {
      throw ApiException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT)
    }
    val passwordHash = passwordEncoder.encode(password)
      ?: throw ApiException("INTERNAL", "Password hashing failed", HttpStatus.INTERNAL_SERVER_ERROR)
    val user = authRepository.createPasswordUser(email, passwordHash, displayName?.trim()?.ifBlank { null })
    return issueAuthResponse(user)
  }

  @Transactional
  fun login(email: String, password: String): AuthResponse {
    val user = authRepository.findUserByEmail(email)
    if (user?.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
      throw ApiException("INVALID_CREDENTIALS", "Email or password is incorrect", HttpStatus.UNAUTHORIZED)
    }
    return issueAuthResponse(user)
  }

  @Transactional
  fun loginWithGoogle(idToken: String): AuthResponse {
    if (idToken.isBlank()) {
      throw ApiException("VALIDATION_ERROR", "idToken must not be blank", HttpStatus.UNPROCESSABLE_ENTITY)
    }
    val tokenInfo = googleAuthService.verify(idToken)
    val user = authRepository.findUserByOAuth(AuthRepository.GOOGLE_PROVIDER, tokenInfo.sub)
      ?: authRepository.createGoogleUser(tokenInfo)
    return issueAuthResponse(user)
  }

  @Transactional
  fun refresh(refreshToken: String): RefreshResponse {
    val tokenHash = hashToken(refreshToken)
    val storedToken = authRepository.findRefreshToken(tokenHash)
    if (storedToken == null || storedToken.revoked || storedToken.expiresAt.isBefore(Instant.now())) {
      throw ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED)
    }
    val user = authRepository.findUserById(storedToken.userId)
      ?: throw ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED)

    authRepository.revokeRefreshToken(tokenHash)
    val newRefreshToken = createRefreshToken(user.id)
    return RefreshResponse(jwtService.createAccessToken(user), newRefreshToken, properties.accessTtlSeconds)
  }

  @Transactional
  fun logout(refreshToken: String) {
    if (refreshToken.isNotBlank()) {
      authRepository.revokeRefreshToken(hashToken(refreshToken))
    }
  }

  fun getUser(userId: UUID): UserDto = authRepository.findUserById(userId)?.toDto()
    ?: throw ApiException("INVALID_TOKEN", "User not found", HttpStatus.UNAUTHORIZED)

  private fun issueAuthResponse(user: AppUser): AuthResponse = AuthResponse(
    user = user.toDto(),
    accessToken = jwtService.createAccessToken(user),
    refreshToken = createRefreshToken(user.id),
    expiresIn = properties.accessTtlSeconds,
  )

  private fun createRefreshToken(userId: UUID): String {
    val bytes = ByteArray(REFRESH_TOKEN_BYTES)
    secureRandom.nextBytes(bytes)
    val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    authRepository.storeRefreshToken(
      userId = userId,
      tokenHash = hashToken(token),
      expiresAt = Instant.now().plus(properties.refreshTtlDays, ChronoUnit.DAYS),
    )
    return token
  }

  private fun hashToken(token: String): String {
    val digest = MessageDigest.getInstance(SHA_256).digest(token.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(digest)
  }

  private fun validateEmailAndPassword(email: String, password: String) {
    if (!EMAIL_REGEX.matches(email.trim()) || password.length < MIN_PASSWORD_LENGTH) {
      throw ApiException("VALIDATION_ERROR", "Invalid email or password", HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }

  private fun AppUser.toDto(): UserDto = UserDto(
    id = id.toString(),
    email = email,
    displayName = displayName,
    emailVerified = emailVerified,
  )

  companion object {
    private const val REFRESH_TOKEN_BYTES = 48
    private const val SHA_256 = "SHA-256"
    private const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    private val secureRandom = SecureRandom()
  }
}
