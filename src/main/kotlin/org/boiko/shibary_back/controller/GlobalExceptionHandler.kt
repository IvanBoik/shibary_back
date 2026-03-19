package org.boiko.shibary_back.controller

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

    /** Ollama (or any upstream) is unreachable */
    @ExceptionHandler(ConnectException::class)
    fun handleConnectionError(ex: ConnectException): ResponseEntity<Map<String, String>> {
        log.error("AI service is unavailable: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(mapOf("error" to "AI service is unavailable, please try again later"))
    }

    /** Malformed request body */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleBadRequestBody(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        log.warn("Malformed request body: {}", ex.message)
        return ResponseEntity
            .badRequest()
            .body(mapOf("error" to "Malformed request body"))
    }

    /** Catch-all for anything unexpected */
    @ExceptionHandler(Exception::class)
    fun handleGenericError(ex: Exception): ResponseEntity<Map<String, String>> {
        log.error("Unexpected error during request processing", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "Internal server error: ${ex.message}"))
    }
}
