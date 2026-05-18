package org.boiko.shibary_back.controller

import org.boiko.shibary_back.dto.SentenceRequest
import org.boiko.shibary_back.dto.SentenceResponse
import org.boiko.shibary_back.service.SentenceGenerationService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
    if (request.offset != null && request.offset !in MIN_COUNT..MAX_COUNT) {
      return ResponseEntity.badRequest()
        .body(mapOf("error" to "Offset must be between $MIN_COUNT and $MAX_COUNT"))
    }

    val result: SentenceResponse = sentenceService.getSentences(
      word = request.word,
      count = request.count,
      offset = request.offset
    )
    return ResponseEntity.ok(result)
  }

  @GetMapping("/word-info")
  fun getWordInfo(@RequestParam word: String): ResponseEntity<Any> {
    if (word.isBlank()) {
      return ResponseEntity.badRequest().body(mapOf("error" to "Word must not be blank"))
    }
    val info = sentenceService.getWordInfo(word)
      ?: return ResponseEntity.status(404)
        .body(mapOf("error" to "Word info for '$word' not found"))
    return ResponseEntity.ok(info)
  }
}
