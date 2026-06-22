package org.boiko.shibary_back.service

import org.boiko.shibary_back.config.S3Properties
import org.boiko.shibary_back.dto.UploadAssetType
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.CORSConfiguration
import software.amazon.awssdk.services.s3.model.CORSRule
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.util.UUID

/**
 * Thin facade over the S3 bucket that stores series assets.
 *
 * Uploads and downloads are performed directly by the browser via short-lived presigned URLs; the
 * backend only mints those URLs, verifies object existence (HEAD) before persisting references,
 * and deletes objects when their owning entity is removed.
 */
@Service
class S3StorageService(
  private val s3Client: S3Client,
  private val s3Presigner: S3Presigner,
  private val props: S3Properties,
) {

  private val log = LoggerFactory.getLogger(javaClass)

  /**
   * Applies a CORS policy to the bucket so the browser can upload/download directly via presigned
   * URLs. Without this, the PUT/GET preflight is rejected with "No 'Access-Control-Allow-Origin'".
   * Best-effort: logs and swallows errors (e.g. missing permission) so startup never fails.
   */
  fun ensureBucketCors() {
    if (props.allowedOrigins.isEmpty()) {
      log.info("S3 CORS auto-config skipped: no s3.allowed-origins configured")
      return
    }
    val rule = CORSRule.builder()
      .allowedOrigins(props.allowedOrigins)
      .allowedMethods("GET", "PUT", "HEAD")
      .allowedHeaders("*")
      .exposeHeaders("ETag")
      .maxAgeSeconds(3000)
      .build()
    try {
      s3Client.putBucketCors(
        PutBucketCorsRequest.builder()
          .bucket(props.bucket)
          .corsConfiguration(CORSConfiguration.builder().corsRules(rule).build())
          .build(),
      )
      log.info("Applied S3 CORS policy to bucket '{}' for origins {}", props.bucket, props.allowedOrigins)
    } catch (ex: Exception) {
      log.warn("Failed to apply S3 CORS policy to bucket '{}': {}", props.bucket, ex.message)
    }
  }

  /** Builds a unique object key under the optional configured prefix, preserving the extension. */
  fun newObjectKey(type: UploadAssetType, fileName: String): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    val suffix = if (ext.isNotBlank()) ".$ext" else ""
    val base = "${type.name.lowercase()}/${UUID.randomUUID()}$suffix"
    val prefix = props.keyPrefix.trim('/')
    return if (prefix.isEmpty()) base else "$prefix/$base"
  }

  /** Mints a presigned PUT URL the browser uses to upload [key] directly to S3. */
  fun presignUpload(key: String, contentType: String): String {
    log.debug("Presigning upload for key '{}' (contentType={}, ttl={})", key, contentType, props.presignTtl)
    val putRequest = PutObjectRequest.builder()
      .bucket(props.bucket)
      .key(key)
      .contentType(contentType)
      .build()
    val presignRequest = PutObjectPresignRequest.builder()
      .signatureDuration(props.presignTtl)
      .putObjectRequest(putRequest)
      .build()
    return try {
      val url = s3Presigner.presignPutObject(presignRequest).url().toString()
      log.info("Minted presigned upload URL for key '{}'", key)
      url
    } catch (ex: Exception) {
      log.error("Failed to presign upload for key '{}': {}", key, ex.message, ex)
      throw ex
    }
  }

  /** Mints a presigned GET URL for displaying/downloading a stored object. */
  fun presignDownload(key: String): String {
    log.debug("Presigning download for key '{}' (ttl={})", key, props.presignTtl)
    val getRequest = GetObjectRequest.builder()
      .bucket(props.bucket)
      .key(key)
      .build()
    val presignRequest = GetObjectPresignRequest.builder()
      .signatureDuration(props.presignTtl)
      .getObjectRequest(getRequest)
      .build()
    return try {
      s3Presigner.presignGetObject(presignRequest).url().toString()
    } catch (ex: Exception) {
      log.error("Failed to presign download for key '{}': {}", key, ex.message, ex)
      throw ex
    }
  }

  /** True if the object exists in the bucket. Used to validate client-supplied keys. */
  fun exists(key: String): Boolean = try {
    s3Client.headObject(HeadObjectRequest.builder().bucket(props.bucket).key(key).build())
    log.debug("S3 object '{}' exists", key)
    true
  } catch (_: NoSuchKeyException) {
    log.debug("S3 object '{}' does not exist", key)
    false
  }

  /**
   * Validates that every supplied key references an existing object, otherwise fails the request.
   * Keeps episode creation atomic: we never persist references to objects that were never uploaded.
   */
  fun requireAllExist(keys: Collection<String>) {
    log.debug("Verifying existence of {} S3 object(s): {}", keys.size, keys)
    val missing = keys.filterNot { exists(it) }
    if (missing.isNotEmpty()) {
      log.warn("Rejected operation: {} of {} S3 object(s) missing: {}", missing.size, keys.size, missing)
      throw ApiException(
        "ASSET_NOT_FOUND",
        "Some files were not uploaded to storage: $missing",
        HttpStatus.UNPROCESSABLE_ENTITY,
      )
    }
  }

  /** Best-effort deletion; logs and swallows errors so cleanup never breaks the main flow. */
  fun deleteQuietly(keys: Collection<String>) {
    val toDelete = keys.filter { it.isNotBlank() }
    if (toDelete.isEmpty()) return
    log.info("Deleting {} S3 object(s): {}", toDelete.size, toDelete)
    toDelete.forEach { key ->
      try {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(props.bucket).key(key).build())
        log.debug("Deleted S3 object '{}'", key)
      } catch (ex: Exception) {
        log.warn("Failed to delete S3 object '{}': {}", key, ex.message)
      }
    }
  }
}
