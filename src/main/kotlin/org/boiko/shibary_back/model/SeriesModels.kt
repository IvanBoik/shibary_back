package org.boiko.shibary_back.model

import java.util.UUID

/** Series metadata row. Binary assets are referenced by S3 object key, not stored inline. */
data class Series(
  val id: UUID,
  val titleRu: String,
  val titleEn: String,
  val genreRu: String,
  val genreEn: String,
  val difficultyRu: String,
  val difficultyEn: String,
  val accentRu: String,
  val accentEn: String,
  val releaseYearsRu: String,
  val releaseYearsEn: String,
  val imageKey: String,
)

data class Season(
  val id: UUID,
  val seriesId: UUID,
  val number: Int,
)

data class Episode(
  val id: UUID,
  val seasonId: UUID,
  val number: Int,
  val titleRu: String,
  val titleEn: String,
  val videoKey: String,
  val subtitlesRuKey: String,
  val subtitlesEnKey: String,
)
