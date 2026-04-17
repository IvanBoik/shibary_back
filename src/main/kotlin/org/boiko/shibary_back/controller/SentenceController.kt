package org.boiko.shibary_back.controller

import org.boiko.shibary_back.dto.SentenceRequest
import org.boiko.shibary_back.dto.SentenceResponse
import org.boiko.shibary_back.service.SentenceGenerationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/sentences")
class SentenceController(private val sentenceService: SentenceGenerationService) {

    companion object {
        private const val MIN_COUNT = 1
        private const val MAX_COUNT = 10
    }

    @PostMapping
    fun getSentences(@RequestBody request: SentenceRequest): ResponseEntity<Any> {
        if (request.word.isBlank()) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Word must not be blank"))
        }
        if (request.count !in MIN_COUNT..MAX_COUNT) {
            return ResponseEntity.badRequest()
                .body(mapOf("error" to "Count must be between $MIN_COUNT and $MAX_COUNT"))
        }
        if (request.offset < 0) {
            return ResponseEntity.badRequest().body(mapOf("error" to "Offset must be >= 0"))
        }

        val result: SentenceResponse = sentenceService.getSentences(
            word = request.word,
            count = request.count,
            offset = request.offset
        )
        return ResponseEntity.ok(result)
    }
}
