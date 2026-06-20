package org.boiko.shibary_back.config

import tools.jackson.databind.ObjectMapper
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.boiko.shibary_back.dto.ApiErrorDto
import org.boiko.shibary_back.dto.ApiErrorResponse
import org.boiko.shibary_back.service.ApiException
import org.boiko.shibary_back.service.JwtService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.context.annotation.Profile
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Profile("!admin")
class JwtAuthenticationFilter(
  private val jwtService: JwtService,
  private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {

  override fun shouldNotFilter(request: HttpServletRequest): Boolean =
    !PROTECTED_PREFIXES.any { request.requestURI.startsWith(it) }

  override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
    try {
      val authHeader = request.getHeader(AUTHORIZATION_HEADER)
      if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
        throw ApiException("INVALID_TOKEN", "Missing bearer token", HttpStatus.UNAUTHORIZED)
      }
      val userId = jwtService.validateAccessToken(authHeader.removePrefix(BEARER_PREFIX).trim())
      SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(userId, null, emptyList())
      filterChain.doFilter(request, response)
    } catch (ex: ApiException) {
      writeUnauthorized(response, ex)
    } finally {
      SecurityContextHolder.clearContext()
    }
  }

  private fun writeUnauthorized(response: HttpServletResponse, ex: ApiException) {
    response.status = ex.status.value()
    response.contentType = MediaType.APPLICATION_JSON_VALUE
    objectMapper.writeValue(response.writer, ApiErrorResponse(ApiErrorDto(ex.code, ex.message)))
  }

  companion object {
    private const val AUTHORIZATION_HEADER = "Authorization"
    private const val BEARER_PREFIX = "Bearer "
    private val PROTECTED_PREFIXES = listOf(
      "/api/sync",
      "/api/auth/me",
      "/api/auth/logout",
      "/api/auth/verify-email",
      "/api/auth/resend-verification",
    )
  }
}
