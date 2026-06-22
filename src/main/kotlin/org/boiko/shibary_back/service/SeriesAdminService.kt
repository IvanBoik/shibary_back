package org.boiko.shibary_back.service

import org.boiko.shibary_back.dto.CreateEpisodeRequest
import org.boiko.shibary_back.dto.CreateSeasonRequest
import org.boiko.shibary_back.dto.EpisodeDto
import org.boiko.shibary_back.dto.Localized
import org.boiko.shibary_back.dto.SeasonDto
import org.boiko.shibary_back.dto.SeriesDto
import org.boiko.shibary_back.dto.SeriesMetaRequest
import org.boiko.shibary_back.dto.SeriesSummaryDto
import org.boiko.shibary_back.model.Episode
import org.boiko.shibary_back.model.Season
import org.boiko.shibary_back.model.Series
import org.boiko.shibary_back.repository.EpisodeRepository
import org.boiko.shibary_back.repository.SeasonRepository
import org.boiko.shibary_back.repository.SeriesRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Series-library management for the admin panel.
 *
 * Series can be built up incrementally: metadata first, then seasons, then episodes. Episode
 * creation is atomic — all fields are mandatory and the three media objects must already exist in
 * S3 (verified via HEAD) before the row is persisted. The seasons count is always derived from the
 * season table, never stored.
 */
@Service
class SeriesAdminService(
  private val seriesRepository: SeriesRepository,
  private val seasonRepository: SeasonRepository,
  private val episodeRepository: EpisodeRepository,
  private val storage: S3StorageService,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  // ----- Series metadata -----------------------------------------------------------------------

  fun listSeries(): List<SeriesSummaryDto> {
    val all = seriesRepository.findAll()
    val counts = seriesRepository.seasonCounts(all.map { it.id })
    return all.map { it.toSummaryDto(counts[it.id] ?: 0) }
  }

  fun getSeries(id: UUID): SeriesDto {
    val series = seriesRepository.findById(id) ?: throw seriesNotFound(id)
    val counts = seriesRepository.seasonCounts(listOf(id))
    return series.toDto(counts[id] ?: 0)
  }

  fun createSeries(request: SeriesMetaRequest): SeriesDto {
    storage.requireAllExist(listOf(request.imageKey))
    val series = request.toSeries(UUID.randomUUID())
    seriesRepository.insert(series)
    log.info("Admin created series '{}'", series.id)
    return series.toDto(seasonsCount = 0)
  }

  /** Updates metadata. If the image changed, the old object is deleted after a successful update. */
  fun updateSeries(id: UUID, request: SeriesMetaRequest): SeriesDto {
    val existing = seriesRepository.findById(id) ?: throw seriesNotFound(id)
    storage.requireAllExist(listOf(request.imageKey))
    val updated = request.toSeries(id)
    if (seriesRepository.update(updated) == 0) throw seriesNotFound(id)
    if (existing.imageKey != updated.imageKey) {
      storage.deleteQuietly(listOf(existing.imageKey))
    }
    log.info("Admin updated series '{}'", id)
    val counts = seriesRepository.seasonCounts(listOf(id))
    return updated.toDto(counts[id] ?: 0)
  }

  /** Deletes the series (seasons/episodes cascade in the DB) and removes all owned S3 objects. */
  @Transactional
  fun deleteSeries(id: UUID) {
    val series = seriesRepository.findById(id) ?: throw seriesNotFound(id)
    val seasons = seasonRepository.findBySeriesId(id)
    val episodes = episodeRepository.findBySeasonIds(seasons.map { it.id })
    if (seriesRepository.delete(id) == 0) throw seriesNotFound(id)
    val keys = buildList {
      add(series.imageKey)
      episodes.forEach { addAll(listOf(it.videoKey, it.subtitlesRuKey, it.subtitlesEnKey)) }
    }
    storage.deleteQuietly(keys)
    log.info("Admin deleted series '{}' with {} season(s)", id, seasons.size)
  }

  // ----- Seasons -------------------------------------------------------------------------------

  fun listSeasons(seriesId: UUID): List<SeasonDto> {
    seriesRepository.findById(seriesId) ?: throw seriesNotFound(seriesId)
    val seasons = seasonRepository.findBySeriesId(seriesId)
    val episodesBySeason = episodeRepository.findBySeasonIds(seasons.map { it.id })
      .groupBy { it.seasonId }
    return seasons.map { season ->
      season.toDto(episodesBySeason[season.id].orEmpty().map { it.toDto() })
    }
  }

  fun createSeason(seriesId: UUID, request: CreateSeasonRequest): SeasonDto {
    seriesRepository.findById(seriesId) ?: throw seriesNotFound(seriesId)
    if (request.number < 1) {
      throw ApiException("VALIDATION_ERROR", "Season number must be positive", HttpStatus.UNPROCESSABLE_ENTITY)
    }
    val season = Season(id = UUID.randomUUID(), seriesId = seriesId, number = request.number)
    try {
      seasonRepository.insert(season)
    } catch (_: DuplicateKeyException) {
      throw ApiException("SEASON_EXISTS", "Season ${request.number} already exists", HttpStatus.CONFLICT)
    }
    log.info("Admin added season {} to series '{}'", request.number, seriesId)
    return season.toDto(emptyList())
  }

  /** Deletes the season (episodes cascade) and removes the owned media objects from S3. */
  @Transactional
  fun deleteSeason(seasonId: UUID) {
    val season = seasonRepository.findById(seasonId) ?: throw seasonNotFound(seasonId)
    val episodes = episodeRepository.findBySeasonId(seasonId)
    if (seasonRepository.delete(seasonId) == 0) throw seasonNotFound(seasonId)
    val keys = episodes.flatMap { listOf(it.videoKey, it.subtitlesRuKey, it.subtitlesEnKey) }
    storage.deleteQuietly(keys)
    log.info("Admin deleted season '{}'", seasonId)
  }

  // ----- Episodes ------------------------------------------------------------------------------

  /**
   * Atomically creates an episode: validates that all media objects exist in S3, then inserts the
   * row. All fields are required (enforced by the non-nullable request type).
   */
  @Transactional
  fun createEpisode(seasonId: UUID, request: CreateEpisodeRequest): EpisodeDto {
    seasonRepository.findById(seasonId) ?: throw seasonNotFound(seasonId)
    if (request.number < 1) {
      throw ApiException("VALIDATION_ERROR", "Episode number must be positive", HttpStatus.UNPROCESSABLE_ENTITY)
    }
    storage.requireAllExist(listOf(request.videoKey, request.subtitlesRuKey, request.subtitlesEnKey))
    val episode = Episode(
      id = UUID.randomUUID(),
      seasonId = seasonId,
      number = request.number,
      titleRu = request.title.ru,
      titleEn = request.title.en,
      videoKey = request.videoKey,
      subtitlesRuKey = request.subtitlesRuKey,
      subtitlesEnKey = request.subtitlesEnKey,
    )
    try {
      episodeRepository.insert(episode)
    } catch (_: DuplicateKeyException) {
      throw ApiException("EPISODE_EXISTS", "Episode ${request.number} already exists", HttpStatus.CONFLICT)
    }
    log.info("Admin added episode {} to season '{}'", request.number, seasonId)
    return episode.toDto()
  }

  /** Deletes the episode row and its three media objects from S3. */
  @Transactional
  fun deleteEpisode(episodeId: UUID) {
    val episode = episodeRepository.findById(episodeId) ?: throw episodeNotFound(episodeId)
    if (episodeRepository.delete(episodeId) == 0) throw episodeNotFound(episodeId)
    storage.deleteQuietly(listOf(episode.videoKey, episode.subtitlesRuKey, episode.subtitlesEnKey))
    log.info("Admin deleted episode '{}'", episodeId)
  }

  // ----- Mapping helpers -----------------------------------------------------------------------

  private fun SeriesMetaRequest.toSeries(id: UUID) = Series(
    id = id,
    titleRu = title.ru, titleEn = title.en,
    genreRu = genre.ru, genreEn = genre.en,
    difficultyRu = difficulty.ru, difficultyEn = difficulty.en,
    accentRu = accent.ru, accentEn = accent.en,
    releaseYearsRu = releaseYears.ru, releaseYearsEn = releaseYears.en,
    imageKey = imageKey,
  )

  private fun Series.toDto(seasonsCount: Int) = SeriesDto(
    id = id.toString(),
    title = Localized(titleRu, titleEn),
    genre = Localized(genreRu, genreEn),
    difficulty = Localized(difficultyRu, difficultyEn),
    accent = Localized(accentRu, accentEn),
    releaseYears = Localized(releaseYearsRu, releaseYearsEn),
    seasonsCount = seasonsCount,
    imageUrl = storage.presignDownload(imageKey),
  )

  private fun Series.toSummaryDto(seasonsCount: Int) = SeriesSummaryDto(
    id = id.toString(),
    title = Localized(titleRu, titleEn),
    genre = Localized(genreRu, genreEn),
    seasonsCount = seasonsCount,
    imageUrl = storage.presignDownload(imageKey),
  )

  private fun Season.toDto(episodes: List<EpisodeDto>) = SeasonDto(
    id = id.toString(),
    number = number,
    episodes = episodes,
  )

  private fun Episode.toDto() = EpisodeDto(
    id = id.toString(),
    number = number,
    title = Localized(titleRu, titleEn),
    videoUrl = storage.presignDownload(videoKey),
    subtitlesRuUrl = storage.presignDownload(subtitlesRuKey),
    subtitlesEnUrl = storage.presignDownload(subtitlesEnKey),
  )

  private fun seriesNotFound(id: UUID) =
    ApiException("SERIES_NOT_FOUND", "Series not found", HttpStatus.NOT_FOUND).also {
      log.warn("Series '{}' not found", id)
    }

  private fun seasonNotFound(id: UUID) =
    ApiException("SEASON_NOT_FOUND", "Season not found", HttpStatus.NOT_FOUND).also {
      log.warn("Season '{}' not found", id)
    }

  private fun episodeNotFound(id: UUID) =
    ApiException("EPISODE_NOT_FOUND", "Episode not found", HttpStatus.NOT_FOUND).also {
      log.warn("Episode '{}' not found", id)
    }
}
