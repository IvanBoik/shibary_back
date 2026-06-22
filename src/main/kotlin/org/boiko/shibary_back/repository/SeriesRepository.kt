package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.Series
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

/**
 * CRUD for the series metadata table plus the derived season count.
 * Used exclusively by the admin panel.
 */
@Repository
class SeriesRepository(private val jdbc: NamedParameterJdbcTemplate) {

  fun insert(series: Series) {
    jdbc.update(
      """
        INSERT INTO series (
          id, title_ru, title_en, genre_ru, genre_en, difficulty_ru, difficulty_en,
          accent_ru, accent_en, release_years_ru, release_years_en, image_key
        ) VALUES (
          :id, :titleRu, :titleEn, :genreRu, :genreEn, :difficultyRu, :difficultyEn,
          :accentRu, :accentEn, :releaseYearsRu, :releaseYearsEn, :imageKey
        )
      """.trimIndent(),
      series.toParams(),
    )
  }

  /** Returns the number of affected rows (0 when the series does not exist). */
  fun update(series: Series): Int = jdbc.update(
    """
      UPDATE series SET
        title_ru = :titleRu, title_en = :titleEn,
        genre_ru = :genreRu, genre_en = :genreEn,
        difficulty_ru = :difficultyRu, difficulty_en = :difficultyEn,
        accent_ru = :accentRu, accent_en = :accentEn,
        release_years_ru = :releaseYearsRu, release_years_en = :releaseYearsEn,
        image_key = :imageKey, updated_at = NOW()
      WHERE id = :id
    """.trimIndent(),
    series.toParams(),
  )

  fun findById(id: UUID): Series? = jdbc.query(
    "SELECT * FROM series WHERE id = :id",
    mapOf("id" to id),
  ) { rs, _ -> rs.toSeries() }.firstOrNull()

  fun findAll(): List<Series> = jdbc.query(
    "SELECT * FROM series ORDER BY created_at DESC, id DESC",
    emptyMap<String, Any>(),
  ) { rs, _ -> rs.toSeries() }

  /** Returns the number of affected rows (0 when the series does not exist). */
  fun delete(id: UUID): Int = jdbc.update("DELETE FROM series WHERE id = :id", mapOf("id" to id))

  /** Season counts per series id, used to populate the derived `seasonsCount` field. */
  fun seasonCounts(seriesIds: Collection<UUID>): Map<UUID, Int> {
    if (seriesIds.isEmpty()) return emptyMap()
    val result = HashMap<UUID, Int>()
    jdbc.query(
      "SELECT series_id, COUNT(*) AS cnt FROM season WHERE series_id IN (:ids) GROUP BY series_id",
      mapOf("ids" to seriesIds),
    ) { rs ->
      result[rs.getObject("series_id", UUID::class.java)] = rs.getInt("cnt")
    }
    return result
  }

  private fun Series.toParams() = MapSqlParameterSource()
    .addValue("id", id)
    .addValue("titleRu", titleRu)
    .addValue("titleEn", titleEn)
    .addValue("genreRu", genreRu)
    .addValue("genreEn", genreEn)
    .addValue("difficultyRu", difficultyRu)
    .addValue("difficultyEn", difficultyEn)
    .addValue("accentRu", accentRu)
    .addValue("accentEn", accentEn)
    .addValue("releaseYearsRu", releaseYearsRu)
    .addValue("releaseYearsEn", releaseYearsEn)
    .addValue("imageKey", imageKey)

  private fun ResultSet.toSeries() = Series(
    id = getObject("id", UUID::class.java),
    titleRu = getString("title_ru"),
    titleEn = getString("title_en"),
    genreRu = getString("genre_ru"),
    genreEn = getString("genre_en"),
    difficultyRu = getString("difficulty_ru"),
    difficultyEn = getString("difficulty_en"),
    accentRu = getString("accent_ru"),
    accentEn = getString("accent_en"),
    releaseYearsRu = getString("release_years_ru"),
    releaseYearsEn = getString("release_years_en"),
    imageKey = getString("image_key"),
  )
}
