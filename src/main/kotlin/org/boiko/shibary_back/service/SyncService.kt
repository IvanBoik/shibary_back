package org.boiko.shibary_back.service

import org.boiko.shibary_back.dto.*
import org.boiko.shibary_back.repository.StoredGameScore
import org.boiko.shibary_back.repository.StoredSettings
import org.boiko.shibary_back.repository.StoredWord
import org.boiko.shibary_back.repository.SyncRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class SyncService(private val syncRepository: SyncRepository) {

  @Transactional
  fun sync(userId: UUID, request: SyncRequest): SyncResponse {
    val cursor = parseCursor(request.cursor)
    val conflicts = mutableListOf<SyncConflictDto>()
    val forcedWords = linkedMapOf<String, StoredWord>()
    var forcedSettings: StoredSettings? = null
    val forcedScores = linkedMapOf<Int, StoredGameScore>()
    var appliedWords = 0
    var appliedScores = 0
    var settingsAccepted = false

    request.clientChanges.words.forEach { word ->
      val stored = syncRepository.findWord(userId, word.uuid)
      if (stored == null || word.updatedAt > stored.dto.updatedAt) {
        syncRepository.upsertWord(userId, word, syncRepository.nextRevision(userId))
        appliedWords++
      } else {
        forcedWords[stored.dto.uuid] = stored
        conflicts += SyncConflictDto(CONFLICT_WORD_TYPE, word.uuid, RESOLUTION_SERVER_WON)
      }
    }

    request.clientChanges.settings?.let { settings ->
      val stored = syncRepository.findSettings(userId)
      if (stored == null || settings.updatedAt > stored.dto.updatedAt) {
        syncRepository.upsertSettings(userId, settings, syncRepository.nextRevision(userId))
        settingsAccepted = true
      } else {
        forcedSettings = stored
      }
    }

    request.clientChanges.gameScores.forEach { score ->
      val stored = syncRepository.findGameScore(userId, score.pairCount)
      if (stored == null || score.bestTimeMillis < stored.dto.bestTimeMillis) {
        val mergedScore = GameScoreSyncDto(score.pairCount, score.bestTimeMillis, score.updatedAt)
        syncRepository.upsertGameScore(userId, mergedScore, syncRepository.nextRevision(userId))
        appliedScores++
      } else if (stored.dto.bestTimeMillis < score.bestTimeMillis) {
        forcedScores[stored.dto.pairCount] = stored
      }
    }

    val pull = pullChanges(userId, cursor, PULL_LIMIT, forcedWords, forcedSettings, forcedScores)
    return SyncResponse(
      cursor = pull.cursor.toString(),
      serverChanges = pull.changes,
      applied = AppliedSyncDto(appliedWords, appliedScores, settingsAccepted),
      conflicts = conflicts.takeIf { it.isNotEmpty() },
    )
  }

  @Transactional(readOnly = true)
  fun bootstrap(userId: UUID): SyncResponse {
    val words = syncRepository.findAllWords(userId)
    val settings = syncRepository.findSettings(userId)
    val gameScores = syncRepository.findAllGameScores(userId)
    val cursor = listOf(
      words.maxOfOrNull { it.revision } ?: 0L,
      settings?.revision ?: 0L,
      gameScores.maxOfOrNull { it.revision } ?: 0L,
      syncRepository.maxRevision(userId),
    ).max()

    return SyncResponse(
      cursor = cursor.toString(),
      serverChanges = SyncChangesDto(
        words = words.map(StoredWord::dto),
        settings = settings?.dto,
        gameScores = gameScores.map(StoredGameScore::dto),
      ),
    )
  }

  private fun pullChanges(
    userId: UUID,
    cursor: Long,
    limit: Int,
    forcedWords: Map<String, StoredWord>,
    forcedSettings: StoredSettings?,
    forcedScores: Map<Int, StoredGameScore>,
  ): PullResult {
    val words = syncRepository.findChangedWords(userId, cursor, limit)
    val remainingLimit = (limit - words.size).coerceAtLeast(0)
    val settings = if (remainingLimit > 0) syncRepository.findChangedSettings(userId, cursor) else null
    val scoreLimit = (remainingLimit - if (settings == null) 0 else 1).coerceAtLeast(0)
    val gameScores = if (scoreLimit > 0) syncRepository.findChangedGameScores(userId, cursor, scoreLimit) else emptyList()

    val mergedWords = mergeWords(words, forcedWords)
    val mergedSettings = settings ?: forcedSettings
    val mergedScores = mergeScores(gameScores, forcedScores)
    val maxRevision = listOf(
      words.maxOfOrNull { it.revision } ?: cursor,
      settings?.revision ?: cursor,
      gameScores.maxOfOrNull { it.revision } ?: cursor,
    ).max()

    return PullResult(
      cursor = maxRevision,
      changes = SyncChangesDto(
        words = mergedWords.map(StoredWord::dto),
        settings = mergedSettings?.dto,
        gameScores = mergedScores.map(StoredGameScore::dto),
      ),
    )
  }

  private fun mergeWords(pulled: List<StoredWord>, forced: Map<String, StoredWord>): List<StoredWord> {
    val result = linkedMapOf<String, StoredWord>()
    pulled.forEach { result[it.dto.uuid] = it }
    forced.forEach { (uuid, word) -> result.putIfAbsent(uuid, word) }
    return result.values.toList()
  }

  private fun mergeScores(pulled: List<StoredGameScore>, forced: Map<Int, StoredGameScore>): List<StoredGameScore> {
    val result = linkedMapOf<Int, StoredGameScore>()
    pulled.forEach { result[it.dto.pairCount] = it }
    forced.forEach { (pairCount, score) -> result.putIfAbsent(pairCount, score) }
    return result.values.toList()
  }

  private fun parseCursor(cursor: String?): Long = cursor?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L

  data class PullResult(
    val cursor: Long,
    val changes: SyncChangesDto,
  )

  companion object {
    private const val PULL_LIMIT = 300
    private const val CONFLICT_WORD_TYPE = "word"
    private const val RESOLUTION_SERVER_WON = "server_won"
  }
}
