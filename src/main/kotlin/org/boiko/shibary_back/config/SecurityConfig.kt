package org.boiko.shibary_back.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern
import java.util.UUID

/**
 * Spring Security setup shared by every deployment of this artifact.
 *
 * - Actuator endpoints ([/actuator]) require HTTP Basic auth (used both by the local app
 *   and scraped by the external Spring Boot Admin server).
 * - Swagger/OpenAPI docs require an admin login.
 * - The [/api] endpoints use stateless JWT auth.
 *
 * The Spring Boot Admin UI itself lives in [AdminServerConfig], which is only active in the
 * `admin` profile (the dedicated admin container).
 */
@Configuration
class SecurityConfig(
  @Value("\${admin.security.username:admin}") private val username: String,
  @Value("\${admin.security.password:}") private val rawPassword: String,
) {

  @Bean
  fun passwordEncoder(): PasswordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()

  @Bean
  fun userDetailsService(encoder: PasswordEncoder): UserDetailsService {
    val password = rawPassword.ifBlank {
      val generated = UUID.randomUUID().toString()
      println("[security] Generated admin password (set ADMIN_PASSWORD to override): $generated")
      generated
    }
    val admin = User.builder()
      .username(username)
      .password(encoder.encode(password))
      .roles(ADMIN_ROLE)
      .build()
    return InMemoryUserDetailsManager(admin)
  }

  @Bean
  @Order(0)
  fun docsSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs.yaml")
      .authorizeHttpRequests { it.anyRequest().hasRole(ADMIN_ROLE) }
      .httpBasic(Customizer.withDefaults())
      .csrf { it.disable() }
    return http.build()
  }

  @Bean
  @Order(1)
  @Profile("!admin")
  fun apiSecurityFilterChain(http: HttpSecurity, jwtAuthenticationFilter: JwtAuthenticationFilter): SecurityFilterChain {
    http
      .securityMatcher("/api/**")
      .csrf { it.disable() }
      .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
      .authorizeHttpRequests { authorize ->
        authorize
          .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/sync/**").authenticated()
          .anyRequest().permitAll()
      }
      .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

    return http.build()
  }

  @Bean
  @Order(2)
  fun actuatorSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    http
      .securityMatcher("/actuator/**")
      .authorizeHttpRequests { authorize ->
        authorize
          .requestMatchers(pathPattern("/actuator/health/**"), pathPattern("/actuator/info")).permitAll()
          .anyRequest().hasRole(ADMIN_ROLE)
      }
      .httpBasic(Customizer.withDefaults())
      // Stateless callbacks scraped by the external Spring Boot Admin server.
      .csrf { it.disable() }

    return http.build()
  }

  companion object {
    private const val ADMIN_ROLE = "ADMIN"
  }
}
