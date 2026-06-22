package org.boiko.shibary_back.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.boiko.shibary_back.dto.CreateEpisodeRequest
import org.boiko.shibary_back.dto.CreateSeasonRequest
import org.boiko.shibary_back.dto.EpisodeDto
import org.boiko.shibary_back.dto.PresignUploadRequest
import org.boiko.shibary_back.dto.PresignUploadResponse
import org.boiko.shibary_back.dto.SeasonDto
import org.boiko.shibary_back.dto.SeriesDto
import org.boiko.shibary_back.dto.SeriesMetaRequest
import org.boiko.shibary_back.dto.SeriesSummaryDto
import org.boiko.shibary_back.dto.UpdateEpisodeRequest
import org.boiko.shibary_back.service.S3StorageService
import org.boiko.shibary_back.service.SeriesAdminService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Admin-only series-library API. Secured by the `ADMIN` role via the admin-panel security filter
 * chain. Files are uploaded directly to S3 by the browser using the presigned URLs minted here.
 */
@Tag(name = "Admin Series", description = "Управление библиотекой сериалов (только для администратора)")
@RestController
@RequestMapping("/admin/api/series")
class SeriesAdminController(
  private val seriesAdminService: SeriesAdminService,
  private val storage: S3StorageService,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  // ----- Uploads -------------------------------------------------------------------------------

  @Operation(summary = "Получить presigned URL для прямой загрузки файла в S3")
  @PostMapping("/uploads/presign")
  fun presignUpload(@RequestBody request: PresignUploadRequest): ResponseEntity<PresignUploadResponse> {
    log.info("Presign upload requested: type={}, fileName='{}'", request.type, request.fileName)
    val key = storage.newObjectKey(request.type, request.fileName)
    val url = storage.presignUpload(key, request.contentType)
    return ResponseEntity.ok(PresignUploadResponse(uploadUrl = url, key = key))
  }

  // ----- Series --------------------------------------------------------------------------------

  @Operation(summary = "Список сериалов")
  @GetMapping
  fun list(): ResponseEntity<List<SeriesSummaryDto>> = ResponseEntity.ok(seriesAdminService.listSeries())

  @Operation(summary = "Получить сериал по id")
  @GetMapping("/{id}")
  fun get(@PathVariable id: UUID): ResponseEntity<SeriesDto> = ResponseEntity.ok(seriesAdminService.getSeries(id))

  @Operation(summary = "Создать сериал (только мета-информация)")
  @PostMapping
  fun create(@RequestBody request: SeriesMetaRequest): ResponseEntity<SeriesDto> =
    ResponseEntity.status(HttpStatus.CREATED).body(seriesAdminService.createSeries(request))

  @Operation(summary = "Обновить мета-информацию сериала")
  @PutMapping("/{id}")
  fun update(@PathVariable id: UUID, @RequestBody request: SeriesMetaRequest): ResponseEntity<SeriesDto> =
    ResponseEntity.ok(seriesAdminService.updateSeries(id, request))

  @Operation(summary = "Удалить сериал со всеми сезонами и сериями")
  @DeleteMapping("/{id}")
  fun delete(@PathVariable id: UUID): ResponseEntity<Void> {
    seriesAdminService.deleteSeries(id)
    return ResponseEntity.noContent().build()
  }

  // ----- Seasons -------------------------------------------------------------------------------

  @Operation(summary = "Список сезонов сериала вместе с сериями")
  @GetMapping("/{id}/seasons")
  fun listSeasons(@PathVariable id: UUID): ResponseEntity<List<SeasonDto>> =
    ResponseEntity.ok(seriesAdminService.listSeasons(id))

  @Operation(summary = "Добавить сезон в сериал")
  @PostMapping("/{id}/seasons")
  fun createSeason(
    @PathVariable id: UUID,
    @RequestBody request: CreateSeasonRequest,
  ): ResponseEntity<SeasonDto> =
    ResponseEntity.status(HttpStatus.CREATED).body(seriesAdminService.createSeason(id, request))

  @Operation(summary = "Удалить сезон со всеми сериями")
  @DeleteMapping("/seasons/{seasonId}")
  fun deleteSeason(@PathVariable seasonId: UUID): ResponseEntity<Void> {
    seriesAdminService.deleteSeason(seasonId)
    return ResponseEntity.noContent().build()
  }

  // ----- Episodes ------------------------------------------------------------------------------

  @Operation(summary = "Добавить серию в сезон (атомарно, все поля обязательны)")
  @PostMapping("/seasons/{seasonId}/episodes")
  fun createEpisode(
    @PathVariable seasonId: UUID,
    @RequestBody request: CreateEpisodeRequest,
  ): ResponseEntity<EpisodeDto> =
    ResponseEntity.status(HttpStatus.CREATED).body(seriesAdminService.createEpisode(seasonId, request))

  @Operation(summary = "Редактировать серию (номер, название, при необходимости заменить файлы)")
  @PutMapping("/episodes/{episodeId}")
  fun updateEpisode(
    @PathVariable episodeId: UUID,
    @RequestBody request: UpdateEpisodeRequest,
  ): ResponseEntity<EpisodeDto> =
    ResponseEntity.ok(seriesAdminService.updateEpisode(episodeId, request))

  @Operation(summary = "Удалить серию")
  @DeleteMapping("/episodes/{episodeId}")
  fun deleteEpisode(@PathVariable episodeId: UUID): ResponseEntity<Void> {
    seriesAdminService.deleteEpisode(episodeId)
    return ResponseEntity.noContent().build()
  }
}
