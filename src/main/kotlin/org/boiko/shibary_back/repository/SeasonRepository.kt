package org.boiko.shibary_back.repository

import org.boiko.shibary_back.model.Season
import org.springframework.dao.DuplicateKeyException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class SeasonRepository(private val jdbc: NamedParameterJdbcTemplate) {

  /** @throws DuplicateKeyException if a season with the same number already exists for the series. */
  fun insert(season: Season) {
    jdbc.update(
      "INSERT INTO season (id, series_id, number) VALUES (:id, :seriesId, :number)",
      mapOf("id" to season.id, "seriesId" to season.seriesId, "number" to season.number),
    )
  }

  fun findById(id: UUID): Season? = jdbc.query(
    "SELECT * FROM season WHERE id = :id",
    mapOf("id" to id),
  ) { rs, _ -> rs.toSeason() }.firstOrNull()

  fun findBySeriesId(seriesId: UUID): List<Season> = jdbc.query(
    "SELECT * FROM season WHERE series_id = :seriesId ORDER BY number",
    mapOf("seriesId" to seriesId),
  ) { rs, _ -> rs.toSeason() }

  /** Returns the number of affected rows (0 when the season does not exist). */
  fun delete(id: UUID): Int = jdbc.update("DELETE FROM season WHERE id = :id", mapOf("id" to id))

  private fun ResultSet.toSeason() = Season(
    id = getObject("id", UUID::class.java),
    seriesId = getObject("series_id", UUID::class.java),
    number = getInt("number"),
  )
}
