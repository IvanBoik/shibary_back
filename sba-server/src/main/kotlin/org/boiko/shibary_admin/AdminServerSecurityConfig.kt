package org.boiko.shibary_admin

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
 * Security for the standalone Spring Boot Admin UI/server.
 *
 * The same admin credentials are used both to log into the UI and by the monitored applications
 * to authenticate their SBA client self-registration (HTTP Basic).
 */
@Configuration
class AdminServerSecurityConfig(
  @Value("\${admin.security.username:admin}") private val username: String,
  @Value("\${admin.security.password:}") private val rawPassword: String,
  private val adminServer: AdminServerProperties,
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
  fun adminUiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    val ctx = adminServer.contextPath

    val successHandler = SavedRequestAwareAuthenticationSuccessHandler().apply {
      setTargetUrlParameter("redirectTo")
      setDefaultTargetUrl("$ctx/")
    }

    http
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
          // SBA client -> server registration and instance callbacks.
          .ignoringRequestMatchers(
            pathPattern(HttpMethod.POST, "$ctx/instances"),
            pathPattern(HttpMethod.DELETE, "$ctx/instances/*"),
          )
      }

    return http.build()
  }

  companion object {
    private const val ADMIN_ROLE = "ADMIN"
  }
}
