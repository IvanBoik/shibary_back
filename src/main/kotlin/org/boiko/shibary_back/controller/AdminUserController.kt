package org.boiko.shibary_back.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.boiko.shibary_back.dto.AdminCreateUserRequest
import org.boiko.shibary_back.dto.AdminUserDto
import org.boiko.shibary_back.service.AdminUserService
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin-only account management API. Secured by the `ADMIN` role (HTTP Basic) via
 * the admin-panel security filter chain. Backs the HTML UI served at `/admin/users.html`.
 */
@Tag(name = "Admin", description = "Управление аккаунтами пользователей (только для администратора)")
@RestController
@RequestMapping("/admin/api/users")
@Profile("!admin")
class AdminUserController(private val adminUserService: AdminUserService) {

  @Operation(summary = "Список всех пользователей")
  @GetMapping
  fun list(): ResponseEntity<List<AdminUserDto>> = ResponseEntity.ok(adminUserService.listUsers())

  @Operation(summary = "Создать нового пользователя (email + пароль)")
  @PostMapping
  fun create(@RequestBody request: AdminCreateUserRequest): ResponseEntity<AdminUserDto> =
    ResponseEntity.status(HttpStatus.CREATED)
      .body(adminUserService.createUser(request.email, request.password, request.displayName))

  @Operation(summary = "Забанить/разбанить пользователя")
  @PostMapping("/{id}/ban")
  fun setBanned(
    @PathVariable id: UUID,
    @RequestParam(defaultValue = "true") banned: Boolean,
  ): ResponseEntity<AdminUserDto> = ResponseEntity.ok(adminUserService.setBanned(id, banned))

  @Operation(summary = "Удалить пользователя")
  @DeleteMapping("/{id}")
  fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
    adminUserService.deleteUser(id)
    return ResponseEntity.noContent().build()
  }
}
