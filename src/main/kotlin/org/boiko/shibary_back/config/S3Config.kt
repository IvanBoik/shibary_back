package org.boiko.shibary_back.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import java.net.URI

/**
 * Wires the S3 clients used for series assets:
 * - [S3Client] for server-side maintenance (HEAD existence checks, object deletion);
 * - [S3Presigner] for minting the presigned URLs that the browser uses to upload/download directly.
 */
@Configuration
class S3Config(private val props: S3Properties) {

  @Bean
  fun s3Client(): S3Client {
    val builder = S3Client.builder()
      .region(Region.of(props.region))
      .credentialsProvider(credentials())
      .serviceConfiguration(serviceConfiguration())
      // Explicit sync HTTP client avoids the "multiple HTTP implementations on classpath" error.
      .httpClient(UrlConnectionHttpClient.create())
    props.endpoint.takeIf { it.isNotBlank() }?.let { builder.endpointOverride(URI.create(it)) }
    return builder.build()
  }

  @Bean
  fun s3Presigner(): S3Presigner {
    val builder = S3Presigner.builder()
      .region(Region.of(props.region))
      .credentialsProvider(credentials())
      .serviceConfiguration(serviceConfiguration())
    props.endpoint.takeIf { it.isNotBlank() }?.let { builder.endpointOverride(URI.create(it)) }
    return builder.build()
  }

  private fun credentials() = StaticCredentialsProvider.create(
    AwsBasicCredentials.create(props.accessKey, props.secretKey),
  )

  private fun serviceConfiguration() = S3Configuration.builder()
    .pathStyleAccessEnabled(props.pathStyleAccess)
    .build()
}
