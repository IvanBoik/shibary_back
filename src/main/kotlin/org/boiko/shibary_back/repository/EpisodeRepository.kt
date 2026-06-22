package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.Episode
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class EpisodeRepository(private val jdbc: NamedParameterJdbcTemplate) {

  /** @throws DuplicateKeyException if an episode with the same number already exists in the season. */
  fun insert(episode: Episode) {
    jdbc.update(
      """
        INSERT INTO episode (
          id, season_id, number, title_ru, title_en, video_key, subtitles_ru_key, subtitles_en_key
        ) VALUES (
          :id, :seasonId, :number, :titleRu, :titleEn, :videoKey, :subtitlesRuKey, :subtitlesEnKey
        )
      """.trimIndent(),
      mapOf(
        "id" to episode.id,
        "seasonId" to episode.seasonId,
        "number" to episode.number,
        "titleRu" to episode.titleRu,
        "titleEn" to episode.titleEn,
        "videoKey" to episode.videoKey,
        "subtitlesRuKey" to episode.subtitlesRuKey,
        "subtitlesEnKey" to episode.subtitlesEnKey,
      ),
    )
  }

  fun findById(id: UUID): Episode? = jdbc.query(
    "SELECT * FROM episode WHERE id = :id",
    mapOf("id" to id),
  ) { rs, _ -> rs.toEpisode() }.firstOrNull()

  fun findBySeasonId(seasonId: UUID): List<Episode> = jdbc.query(
    "SELECT * FROM episode WHERE season_id = :seasonId ORDER BY number",
    mapOf("seasonId" to seasonId),
  ) { rs, _ -> rs.toEpisode() }

  /** All episodes belonging to any of the given seasons (used to batch-load a series detail page). */
  fun findBySeasonIds(seasonIds: Collection<UUID>): List<Episode> {
    if (seasonIds.isEmpty()) return emptyList()
    return jdbc.query(
      "SELECT * FROM episode WHERE season_id IN (:seasonIds) ORDER BY number",
      mapOf("seasonIds" to seasonIds),
    ) { rs, _ -> rs.toEpisode() }
  }

  /** Returns the number of affected rows (0 when the episode does not exist). */
  fun delete(id: UUID): Int = jdbc.update("DELETE FROM episode WHERE id = :id", mapOf("id" to id))

  private fun ResultSet.toEpisode() = Episode(
    id = getObject("id", UUID::class.java),
    seasonId = getObject("season_id", UUID::class.java),
    number = getInt("number"),
    titleRu = getString("title_ru"),
    titleEn = getString("title_en"),
    videoKey = getString("video_key"),
    subtitlesRuKey = getString("subtitles_ru_key"),
    subtitlesEnKey = getString("subtitles_en_key"),
  )
}
