package org.boiko.shibary_back.dto

/** Bilingual text value used by every textual field of a series. */
data class Localized(
  val ru: String,
  val en: String,
)

/**
 * Full series representation returned to the admin panel. Binary assets are exposed as presigned
 * download URLs (valid for a limited time), never as raw object keys.
 */
data class SeriesDto(
  val id: String,
  val title: Localized,
  val genre: Localized,
  val difficulty: Localized,
  val accent: Localized,
  /** Release years as free-form text, e.g. "2009" or "2009-2013" (no language split: digits only). */
  val releaseYears: String,
  val seasonsCount: Int,
  val imageUrl: String,
)

/** Lightweight series row for the list view (no season/episode details). */
data class SeriesSummaryDto(
  val id: String,
  val title: Localized,
  val genre: Localized,
  val seasonsCount: Int,
  val imageUrl: String,
)

/**
 * Payload to create/update the series metadata only (seasons and episodes are managed separately).
 * [imageKey] must reference an object already uploaded to S3 via a presigned URL.
 */
data class SeriesMetaRequest(
  val title: Localized,
  val genre: Localized,
  val difficulty: Localized,
  val accent: Localized,
  /** Release years as free-form text, e.g. "2009" or "2009-2013". */
  val releaseYears: String,
  val imageKey: String,
)

/** One season with its episodes, as shown on the series detail page. */
data class SeasonDto(
  val id: String,
  val number: Int,
  val episodes: List<EpisodeDto>,
)

data class EpisodeDto(
  val id: String,
  val number: Int,
  val title: String,
  val videoUrl: String,
  val subtitlesRuUrl: String,
  val subtitlesEnUrl: String,
)

/** Adds a new season (by its ordinal number) to an existing series. */
data class CreateSeasonRequest(
  val number: Int,
)

/**
 * Creates an episode atomically: every field is mandatory and all three media objects
 * ([videoKey], [subtitlesRuKey], [subtitlesEnKey]) must already exist in S3.
 */
data class CreateEpisodeRequest(
  val number: Int,
  val title: String,
  val videoKey: String,
  val subtitlesRuKey: String,
  val subtitlesEnKey: String,
)

/**
 * Updates an episode. [number] and [title] are always required. Each media key is optional: a
 * non-null value means the file was re-uploaded and replaces the current one (the old object is
 * deleted from S3); null means keep the existing file.
 */
data class UpdateEpisodeRequest(
  val number: Int,
  val title: String,
  val videoKey: String? = null,
  val subtitlesRuKey: String? = null,
  val subtitlesEnKey: String? = null,
)

/** Kind of asset the client wants to upload; determines the S3 key prefix and allowed usage. */
enum class UploadAssetType {
  IMAGE,
  VIDEO,
  SUBTITLES,
}

/** Request to mint a presigned PUT URL for a direct browser-to-S3 upload. */
data class PresignUploadRequest(
  val type: UploadAssetType,
  /** Original file name; only its extension is used to build the object key. */
  val fileName: String,
  /** MIME type the client will send with the PUT request. */
  val contentType: String,
)

/**
 * Result of a presign request: [uploadUrl] is the short-lived PUT URL, [key] is the stable object
 * key the client must echo back when saving the series/episode.
 */
data class PresignUploadResponse(
  val uploadUrl: String,
  val key: String,
)
