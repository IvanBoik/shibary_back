package org.boiko.shibary_back.controller

import org.boiko.shibary_back.dto.ApiErrorDto
import org.boiko.shibary_back.dto.ApiErrorResponse
import org.boiko.shibary_back.service.ApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.ConnectException

@RestControllerAdvice
class GlobalExceptionHandler {

  private val log = LoggerFactory.getLogger(javaClass)

  @ExceptionHandler(ApiException::class)
  fun handleApiException(ex: ApiException): ResponseEntity<ApiErrorResponse> =
    ResponseEntity.status(ex.status).body(error(ex.code, ex.message))

  /** Chad API or DB is unreachable */
  @ExceptionHandler(ConnectException::class)
  fun handleConnectionError(ex: ConnectException): ResponseEntity<ApiErrorResponse> {
    log.error("External service is unavailable: {}", ex.message)
    return ResponseEntity
      .status(HttpStatus.SERVICE_UNAVAILABLE)
      .body(error("INTERNAL", "External service is unavailable, please try again later"))
  }

  /** Malformed request body */
  @ExceptionHandler(HttpMessageNotReadableException::class)
  fun handleBadRequestBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiErrorResponse> {
    log.warn("Malformed request body: {}", ex.message)
    return ResponseEntity
      .status(HttpStatus.UNPROCESSABLE_ENTITY)
      .body(error("VALIDATION_ERROR", "Malformed request body"))
  }

  /** Catch-all for anything unexpected */
  @ExceptionHandler(Exception::class)
  fun handleGenericError(ex: Exception): ResponseEntity<ApiErrorResponse> {
    log.error("Unexpected error during request processing", ex)
    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(error("INTERNAL", "Internal server error"))
  }

  private fun error(code: String, message: String): ApiErrorResponse = ApiErrorResponse(ApiErrorDto(code, message))
}
