package org.boiko.shibary_back.config

import de.codecentric.boot.admin.server.config.AdminServerProperties
import de.codecentric.boot.admin.server.config.EnableAdminServer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern

/**
 * Standalone Spring Boot Admin server.
 *
 * Active only in the `admin` profile, i.e. in the dedicated admin container. Running the admin UI
 * as a separate process means it stays up and observable even when the main application fails to
 * start, so its DOWN/OFFLINE state and last known health/logs remain visible.
 *
 * Monitored applications register themselves here as SBA clients (see [SbaClientConfig], which is
 * disabled in this profile).
 */
@Configuration
@Profile("admin")
@EnableAdminServer
class AdminServerConfig(
  private val adminServer: AdminServerProperties,
) {

  @Bean
  @Order(0)
  fun adminUiSecurityFilterChain(http: HttpSecurity): SecurityFilterChain {
    val ctx = adminServer.contextPath

    val successHandler = SavedRequestAwareAuthenticationSuccessHandler().apply {
      setTargetUrlParameter("redirectTo")
      setDefaultTargetUrl("$ctx/")
    }

    http
      .securityMatcher("$ctx/**")
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
}
