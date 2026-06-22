package org.boiko.shibary_back.config

import de.codecentric.boot.admin.client.config.ClientProperties
import de.codecentric.boot.admin.client.registration.RegistrationClient
import de.codecentric.boot.admin.client.registration.RestClientRegistrationClient
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder
import org.springframework.boot.http.client.HttpClientSettings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.web.client.RestClient
import java.nio.charset.StandardCharsets
import java.util.Base64

/**
 * Overrides Spring Boot Admin client's default [RegistrationClient] so that the
 * Basic-auth header for self-registration is encoded in UTF-8 instead of ISO-8859-1.
 *
 * Reason: SBA 4.0.4 uses Spring's [org.springframework.http.client.support.BasicAuthenticationInterceptor],
 * which defaults to ISO-8859-1 and throws "Username or password contains characters that
 * cannot be encoded to ISO-8859-1" when credentials contain non-Latin-1 characters
 * (Cyrillic, emoji, etc.). RFC 7617 explicitly allows UTF-8, and Spring Security's
 * BasicAuthenticationFilter accepts UTF-8 by default.
 */
@Configuration(proxyBeanMethods = false)
class SbaClientConfig {

  @Bean
  fun registrationClient(
    client: ClientProperties,
    restClientBuilder: RestClient.Builder,
  ): RegistrationClient {
    val factorySettings = HttpClientSettings.defaults()
      .withConnectTimeout(client.connectTimeout)
      .withReadTimeout(client.readTimeout)

    restClientBuilder.requestFactory(ClientHttpRequestFactoryBuilder.detect().build(factorySettings))

    val username = client.username
    val password = client.password
    if (!username.isNullOrEmpty() && !password.isNullOrEmpty()) {
      val token = Base64.getEncoder()
        .encodeToString("$username:$password".toByteArray(StandardCharsets.UTF_8))
      restClientBuilder.requestInterceptor { request, body, execution ->
        request.headers.set(HttpHeaders.AUTHORIZATION, "Basic $token")
        execution.execute(request, body)
      }
    }

    return RestClientRegistrationClient(restClientBuilder.build())
  }
}
