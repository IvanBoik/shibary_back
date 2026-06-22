package org.boiko.shibary_back.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/**
 * Connection settings for the S3-compatible object storage that backs the series library.
 *
 * Works with AWS S3, MinIO and Yandex Object Storage. For non-AWS backends set [endpoint] and
 * usually [pathStyleAccess] = true.
 */
@ConfigurationProperties(prefix = "s3")
data class S3Properties(
  /** Bucket that holds all series assets (images, videos, subtitles). */
  val bucket: String = "",
  /**
   * Optional key prefix ("folder") prepended to every object key, e.g. "series". Leading/trailing
   * slashes are ignored. Lets several apps share one bucket without colliding.
   */
  val keyPrefix: String = "",
  /** Region identifier. Required by the SDK even for non-AWS backends (use any valid value). */
  val region: String = "us-east-1",
  /** Custom endpoint for MinIO/Yandex; leave blank to use the default AWS endpoint. */
  val endpoint: String = "",
  val accessKey: String = "",
  val secretKey: String = "",
  /** Path-style addressing (bucket in the path, not the host). Needed by most non-AWS backends. */
  val pathStyleAccess: Boolean = true,
  /** How long minted presigned upload/download URLs stay valid. */
  val presignTtl: Duration = Duration.ofMinutes(15),
)
