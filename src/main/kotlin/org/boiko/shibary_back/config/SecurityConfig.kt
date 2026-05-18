package org.boiko.shibary_back.config

import de.codecentric.boot.admin.server.config.AdminServerProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern
import java.util.UUID

/**
 * Spring Security setup for Spring Boot Admin UI and Actuator endpoints.
 *
 * - The Admin UI ([/admin]) and Actuator endpoints ([/actuator]) require login.
 * - All other application endpoints stay open (current API has no auth).
 * - HTTP Basic is enabled so the SBA client can self-register, and CSRF is disabled
 *   for the SBA registration/instance callbacks and actuator endpoints.
 */
@Configuration
class SecurityConfig(
  private val adminServer: AdminServerProperties,
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
      .roles("ADMIN")
      .build()
    return InMemoryUserDetailsManager(admin)
  }

  @Bean
  fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
    val ctx = adminServer.contextPath

    val successHandler = SavedRequestAwareAuthenticationSuccessHandler().apply {
      setTargetUrlParameter("redirectTo")
      setDefaultTargetUrl("$ctx/")
    }

    http
      .securityMatcher("$ctx/**", "/actuator/**")
      .authorizeHttpRequests { authorize ->
        authorize
          .requestMatchers(
            pathPattern("$ctx/assets/**"),
            pathPattern("$ctx/login"),
            pathPattern("$ctx/variables.css"),
          ).permitAll()
          .anyRequest().authenticated()
      }
      .formLogin { form ->
        form.loginPage("$ctx/login").successHandler(successHandler).permitAll()
      }
      .logout { logout -> logout.logoutUrl("$ctx/logout") }
      .httpBasic(Customizer.withDefaults())
      .csrf { csrf ->
        csrf
          .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
          // SBA client -> server registration and actuator callbacks
          .ignoringRequestMatchers(
            pathPattern(HttpMethod.POST, "$ctx/instances"),
            pathPattern(HttpMethod.DELETE, "$ctx/instances/*"),
            pathPattern("/actuator/**"),
          )
      }

    return http.build()
  }
}
