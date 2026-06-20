package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.AuthProperties
import org.boiko.shibary_back.dto.AuthResponse
import org.boiko.shibary_back.dto.RefreshResponse
import org.boiko.shibary_back.dto.UserDto
import org.boiko.shibary_back.model.AppUser
import org.boiko.shibary_back.model.GoogleTokenInfo
import org.boiko.shibary_back.repository.AuthRepository
import org.slf4j.LoggerFactory
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
  private val emailVerificationService: EmailVerificationService,
  private val properties: AuthProperties,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  @Transactional
  fun register(email: String, password: String, displayName: String?): AuthResponse {
    validateEmailAndPassword(email, password)
    if (authRepository.findUserByEmail(email) != null) {
      log.warn("Registration rejected: email '{}' is already registered", email)
      throw ApiException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT)
    }
    val passwordHash = passwordEncoder.encode(password)
      ?: run {
        log.error("Password hashing returned null for email '{}'", email)
        throw ApiException("INTERNAL", "Password hashing failed", HttpStatus.INTERNAL_SERVER_ERROR)
      }
    val user = authRepository.createPasswordUser(email, passwordHash, displayName?.trim()?.ifBlank { null })
    emailVerificationService.startVerification(user)
    return issueAuthResponse(user)
  }

  @Transactional
  fun login(email: String, password: String): AuthResponse {
    val user = authRepository.findUserByEmail(email)
    if (user?.passwordHash == null || !passwordEncoder.matches(password, user.passwordHash)) {
      log.warn("Login failed: invalid credentials for email '{}'", email)
      throw ApiException("INVALID_CREDENTIALS", "Email or password is incorrect", HttpStatus.UNAUTHORIZED)
    }
    ensureNotBanned(user)
    return issueAuthResponse(user)
  }

  @Transactional
  fun loginWithGoogle(idToken: String): AuthResponse {
    if (idToken.isBlank()) {
      log.warn("Google login rejected: idToken is blank")
      throw ApiException("VALIDATION_ERROR", "idToken must not be blank", HttpStatus.UNPROCESSABLE_ENTITY)
    }
    val tokenInfo = googleAuthService.verify(idToken)
    val user = authRepository.findUserByOAuth(AuthRepository.GOOGLE_PROVIDER, tokenInfo.sub)
      ?: linkOrCreateGoogleUser(tokenInfo)
    ensureNotBanned(user)
    return issueAuthResponse(user)
  }

  /**
   * Resolves the user for a first-time Google sign-in: if an account with the same email already
   * exists (e.g. created via email/password), links the Google provider to it instead of inserting
   * a new row (which would violate the unique email constraint); otherwise creates a fresh user.
   */
  private fun linkOrCreateGoogleUser(tokenInfo: GoogleTokenInfo): AppUser {
    val existingByEmail = tokenInfo.email?.let(authRepository::findUserByEmail)
    if (existingByEmail != null) {
      authRepository.linkGoogleAccount(existingByEmail.id, tokenInfo.sub)
      log.info("Linked Google account to existing user '{}' by email", existingByEmail.id)
      return existingByEmail
    }
    return authRepository.createGoogleUser(tokenInfo)
  }

  @Transactional
  fun refresh(refreshToken: String): RefreshResponse {
    val tokenHash = hashToken(refreshToken)
    val storedToken = authRepository.findRefreshToken(tokenHash)
    if (storedToken == null || storedToken.revoked || storedToken.expiresAt.isBefore(Instant.now())) {
      log.warn("Refresh rejected: refresh token is missing, revoked or expired")
      throw ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED)
    }
    val user = authRepository.findUserById(storedToken.userId)
      ?: run {
        log.warn("Refresh rejected: user '{}' for refresh token not found", storedToken.userId)
        throw ApiException("INVALID_TOKEN", "Invalid refresh token", HttpStatus.UNAUTHORIZED)
      }
    ensureNotBanned(user)

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

  fun getUser(userId: UUID): UserDto {
    return authRepository.findUserById(userId)?.toDto()
      ?: run {
        log.warn("User '{}' not found", userId)
        throw ApiException("INVALID_TOKEN", "User not found", HttpStatus.UNAUTHORIZED)
      }
  }

  private fun ensureNotBanned(user: AppUser) {
    if (user.banned) {
      log.warn("Access rejected: user '{}' is banned", user.id)
      throw ApiException("ACCOUNT_BANNED", "This account has been banned", HttpStatus.FORBIDDEN)
    }
  }

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
