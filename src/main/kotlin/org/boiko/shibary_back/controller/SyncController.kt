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
import org.boiko.shibary_back.dto.SyncRequest
import org.boiko.shibary_back.dto.SyncResponse
import org.boiko.shibary_back.service.SyncService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Sync", description = "Двусторонняя дельта-синхронизация состояния между устройствами (Bearer)")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
@RestController
@RequestMapping("/api/sync")
class SyncController(private val syncService: SyncService) {

  @Operation(
    summary = "Дельта-синхронизация: push клиентских изменений + pull серверных по курсору",
    description = "LWW по updatedAt для words/settings; min(bestTimeMillis) для gameScores. " +
      "Пустой serverChanges и неизменный cursor означают конец пагинации.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Применённые изменения и серверная дельта",
        content = [Content(schema = Schema(implementation = SyncResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_TOKEN / TOKEN_EXPIRED",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @PostMapping
  fun sync(
    @Parameter(hidden = true) @AuthenticationPrincipal userId: UUID,
    @RequestBody request: SyncRequest,
  ): ResponseEntity<SyncResponse> = ResponseEntity.ok(syncService.sync(userId, request))

  @Operation(
    summary = "Полный снапшот всех данных пользователя",
    description = "Для нового устройства или восстановления после потери локальной БД. Идемпотентно.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "200",
        description = "Полный снапшот + актуальный cursor",
        content = [Content(schema = Schema(implementation = SyncResponse::class))],
      ),
      ApiResponse(
        responseCode = "401",
        description = "INVALID_TOKEN / TOKEN_EXPIRED",
        content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
      ),
    ],
  )
  @GetMapping("/bootstrap")
  fun bootstrap(@Parameter(hidden = true) @AuthenticationPrincipal userId: UUID): ResponseEntity<SyncResponse> =
    ResponseEntity.ok(syncService.bootstrap(userId))
}
