package org.boiko.shibary_back.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.boiko.shibary_back.config.OpenApiConfig
import org.boiko.shibary_back.dto.ApiErrorResponse
import org.boiko.shibary_back.dto.AuthResponse
import org.boiko.shibary_back.dto.GoogleAuthRequest
import org.boiko.shibary_back.dto.LoginRequest
import org.boiko.shibary_back.dto.LogoutRequest
import org.boiko.shibary_back.dto.RefreshRequest
import org.boiko.shibary_back.dto.RefreshResponse
import org.boiko.shibary_back.dto.RegisterRequest
import org.boiko.shibary_back.dto.UserDto
import org.boiko.shibary_back.service.AuthService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Auth", description = "Регистрация, вход (email/пароль и Google) и управление JWT-сессиями")
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

  @Operation(summary = "Регистрация по email и паролю")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "201",
        description = "Пользователь создан, выданы токены",
        content = [Content(schema = Schema(implementation = AuthResponse::class))],
      ),
      ApiResponse(
        responseCode = "409",
        description = "EMAIL_ALREADY_EXISTS — email уже занят",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "422",
        description = "VALIDATION_ERROR — некорректный ввод",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @PostMapping("/register")
  fun register(@RequestBody request: RegisterRequest): ResponseEntity<AuthResponse> =
    ResponseEntity.status(HttpStatus.CREATED)
      .body(authService.register(request.email, request.password, request.displayName))

  @Operation(summary = "Вход по email и паролю")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Успешный вход",
        content = [Content(schema = Schema(implementation = AuthResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_CREDENTIALS — неверная пара email/пароль",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @PostMapping("/login")
  fun login(@RequestBody request: LoginRequest): ResponseEntity<AuthResponse> =
    ResponseEntity.ok(authService.login(request.email, request.password))

  @Operation(summary = "Вход/регистрация через Google idToken")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Успешный вход",
        content = [Content(schema = Schema(implementation = AuthResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_GOOGLE_TOKEN — невалидный idToken",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @PostMapping("/google")
  fun google(@RequestBody request: GoogleAuthRequest): ResponseEntity<AuthResponse> =
    ResponseEntity.ok(authService.loginWithGoogle(request.idToken))

  @Operation(summary = "Обновление пары токенов по refresh (с ротацией)")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Выдана новая пара токенов",
        content = [Content(schema = Schema(implementation = RefreshResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_TOKEN / TOKEN_EXPIRED — refresh невалиден, истёк или отозван",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @PostMapping("/refresh")
  fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<RefreshResponse> =
    ResponseEntity.ok(authService.refresh(request.refreshToken))

  @Operation(summary = "Выход — отзыв refresh-токена")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @ApiResponses(value = [ApiResponse(responseCode = "204", description = "Refresh отозван (тело отсутствует)")])
  @PostMapping("/logout")
  fun logout(@RequestBody request: LogoutRequest): ResponseEntity<Void> {
    authService.logout(request.refreshToken)
    return ResponseEntity.noContent().build()
  }

  @Operation(summary = "Профиль текущего пользователя")
  @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Профиль пользователя",
        content = [Content(schema = Schema(implementation = UserDto::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_TOKEN / TOKEN_EXPIRED",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/me")
  fun me(@Parameter(hidden = true) @AuthenticationPrincipal userId: UUID): ResponseEntity<UserDto> =
    ResponseEntity.ok(authService.getUser(userId))
}
