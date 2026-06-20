package org.boiko.shibary_back.service

import org.boiko.shibary_back.dto.AdminUserDto
import org.boiko.shibary_back.model.AppUser
import org.boiko.shibary_back.repository.AdminUserRepository
import org.boiko.shibary_back.repository.AuthRepository
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Account management used by the admin panel: list, create, ban/unban and delete users.
 *
 * Disabled in the `admin` profile, which runs without a datasource.
 */
@Service
@Profile("!admin")
class AdminUserService(
  private val adminUserRepository: AdminUserRepository,
  private val authRepository: AuthRepository,
  private val passwordEncoder: PasswordEncoder,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  fun listUsers(): List<AdminUserDto> = adminUserRepository.listUsers().map { it.toAdminDto() }

  @Transactional
  fun createUser(email: String, password: String, displayName: String?): AdminUserDto {
    validateEmailAndPassword(email, password)
    if (authRepository.findUserByEmail(email) != null) {
      log.warn("Admin create rejected: email '{}' already exists", email)
      throw ApiException("EMAIL_ALREADY_EXISTS", "Email is already registered", HttpStatus.CONFLICT)
    }
    val passwordHash = passwordEncoder.encode(password)
      ?: throw ApiException("INTERNAL", "Password hashing failed", HttpStatus.INTERNAL_SERVER_ERROR)
    val user = authRepository.createPasswordUser(email, passwordHash, displayName?.trim()?.ifBlank { null })
    log.info("Admin created user '{}'", user.id)
    return user.toAdminDto()
  }

  /** Bans the user and revokes every refresh token so existing sessions cannot be refreshed. */
  @Transactional
  fun setBanned(userId: UUID, banned: Boolean): AdminUserDto {
    if (adminUserRepository.setBanned(userId, banned) == 0) {
      throw userNotFound(userId)
    }
    if (banned) {
      authRepository.revokeAllRefreshTokensForUser(userId)
    }
    log.info("Admin set banned={} for user '{}'", banned, userId)
    return authRepository.findUserById(userId)?.toAdminDto() ?: throw userNotFound(userId)
  }

  @Transactional
  fun deleteUser(userId: UUID) {
    if (adminUserRepository.deleteUser(userId) == 0) {
      throw userNotFound(userId)
    }
    log.info("Admin deleted user '{}'", userId)
  }

  private fun userNotFound(userId: UUID): ApiException {
    log.warn("Admin operation rejected: user '{}' not found", userId)
    return ApiException("USER_NOT_FOUND", "User not found", HttpStatus.NOT_FOUND)
  }

  private fun validateEmailAndPassword(email: String, password: String) {
    if (!EMAIL_REGEX.matches(email.trim()) || password.length < MIN_PASSWORD_LENGTH) {
      throw ApiException("VALIDATION_ERROR", "Invalid email or password", HttpStatus.UNPROCESSABLE_ENTITY)
    }
  }

  private fun AppUser.toAdminDto(): AdminUserDto = AdminUserDto(
    id = id.toString(),
    email = email,
    displayName = displayName,
    emailVerified = emailVerified,
    banned = banned,
  )

  companion object {
    private const val MIN_PASSWORD_LENGTH = 6
    private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
  }
}
