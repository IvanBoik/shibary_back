package org.boiko.shibary_back.repository

import org.boiko.shibary_back.dto.GameScoreSyncDto
import org.boiko.shibary_back.dto.SettingsSyncDto
import org.boiko.shibary_back.dto.WordSyncDto
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.util.UUID

@Repository
class SyncRepository(private val jdbc: NamedParameterJdbcTemplate) {

  fun nextRevision(userId: UUID): Long {
    jdbc.update(
      """
        INSERT INTO user_sync_state (user_id, current_revision)
        VALUES (:userId, 0)
        ON CONFLICT (user_id) DO NOTHING
      """.trimIndent(),
      mapOf("userId" to userId),
    )
    return jdbc.queryForObject(
      """
        UPDATE user_sync_state
        SET current_revision = current_revision + 1
        WHERE user_id = :userId
        RETURNING current_revision
      """.trimIndent(),
      mapOf("userId" to userId),
      Long::class.java,
    ) ?: error("Revision was not generated")
  }

  fun findWord(userId: UUID, uuid: String): StoredWord? = jdbc.query(
    "SELECT * FROM user_words WHERE user_id = :userId AND uuid = :uuid",
    mapOf("userId" to userId, "uuid" to uuid),
  ) { rs, _ -> StoredWord(rs.toWordDto(), rs.getLong("server_revision")) }.firstOrNull()

  fun upsertWord(userId: UUID, word: WordSyncDto, revision: Long) {
    jdbc.update(
      """
        INSERT INTO user_words (
          user_id, uuid, english, russian, repeat_days, added_date, is_learned, stage,
          next_review_at, last_review_at, correct_streak, is_deleted, updated_at, server_revision
        ) VALUES (
          :userId, :uuid, :english, :russian, :repeatDays, :addedDate, :isLearned, :stage,
          :nextReviewAt, :lastReviewAt, :correctStreak, :isDeleted, :updatedAt, :serverRevision
        )
        ON CONFLICT (user_id, uuid) DO UPDATE SET
          english = EXCLUDED.english,
          russian = EXCLUDED.russian,
          repeat_days = EXCLUDED.repeat_days,
          added_date = EXCLUDED.added_date,
          is_learned = EXCLUDED.is_learned,
          stage = EXCLUDED.stage,
          next_review_at = EXCLUDED.next_review_at,
          last_review_at = EXCLUDED.last_review_at,
          correct_streak = EXCLUDED.correct_streak,
          is_deleted = EXCLUDED.is_deleted,
          updated_at = EXCLUDED.updated_at,
          server_revision = EXCLUDED.server_revision
      """.trimIndent(),
      word.params(userId, revision),
    )
  }

  fun findSettings(userId: UUID): StoredSettings? = jdbc.query(
    "SELECT * FROM user_settings WHERE user_id = :userId",
    mapOf("userId" to userId),
  ) { rs, _ -> StoredSettings(rs.toSettingsDto(), rs.getLong("server_revision")) }.firstOrNull()

  fun upsertSettings(userId: UUID, settings: SettingsSyncDto, revision: Long) {
    jdbc.update(
      """
        INSERT INTO user_settings (
          user_id, language, theme, enabled_question_types, repetition_mode, daily_review_limit,
          new_words_per_day, reminders_enabled, reminder_hour, reminder_minute, updated_at, server_revision
        ) VALUES (
          :userId, :language, :theme, :enabledQuestionTypes, :repetitionMode, :dailyReviewLimit,
          :newWordsPerDay, :remindersEnabled, :reminderHour, :reminderMinute, :updatedAt, :serverRevision
        )
        ON CONFLICT (user_id) DO UPDATE SET
          language = EXCLUDED.language,
          theme = EXCLUDED.theme,
          enabled_question_types = EXCLUDED.enabled_question_types,
          repetition_mode = EXCLUDED.repetition_mode,
          daily_review_limit = EXCLUDED.daily_review_limit,
          new_words_per_day = EXCLUDED.new_words_per_day,
          reminders_enabled = EXCLUDED.reminders_enabled,
          reminder_hour = EXCLUDED.reminder_hour,
          reminder_minute = EXCLUDED.reminder_minute,
          updated_at = EXCLUDED.updated_at,
          server_revision = EXCLUDED.server_revision
      """.trimIndent(),
      mapOf(
        "userId" to userId,
        "language" to settings.language,
        "theme" to settings.theme,
        "enabledQuestionTypes" to settings.enabledQuestionTypes.toTypedArray(),
        "repetitionMode" to settings.repetitionMode,
        "dailyReviewLimit" to settings.dailyReviewLimit,
        "newWordsPerDay" to settings.newWordsPerDay,
        "remindersEnabled" to settings.remindersEnabled,
        "reminderHour" to settings.reminderHour,
        "reminderMinute" to settings.reminderMinute,
        "updatedAt" to settings.updatedAt,
        "serverRevision" to revision,
      ),
    )
  }

  fun findGameScore(userId: UUID, pairCount: Int): StoredGameScore? = jdbc.query(
    "SELECT * FROM user_game_scores WHERE user_id = :userId AND pair_count = :pairCount",
    mapOf("userId" to userId, "pairCount" to pairCount),
  ) { rs, _ -> StoredGameScore(rs.toGameScoreDto(), rs.getLong("server_revision")) }.firstOrNull()

  fun upsertGameScore(userId: UUID, score: GameScoreSyncDto, revision: Long) {
    jdbc.update(
      """
        INSERT INTO user_game_scores (user_id, pair_count, best_time_millis, updated_at, server_revision)
        VALUES (:userId, :pairCount, :bestTimeMillis, :updatedAt, :serverRevision)
        ON CONFLICT (user_id, pair_count) DO UPDATE SET
          best_time_millis = EXCLUDED.best_time_millis,
          updated_at = EXCLUDED.updated_at,
          server_revision = EXCLUDED.server_revision
      """.trimIndent(),
      mapOf(
        "userId" to userId,
        "pairCount" to score.pairCount,
        "bestTimeMillis" to score.bestTimeMillis,
        "updatedAt" to score.updatedAt,
        "serverRevision" to revision,
      ),
    )
  }

  fun findChangedWords(userId: UUID, cursor: Long, limit: Int): List<StoredWord> = jdbc.query(
    """
      SELECT * FROM user_words
      WHERE user_id = :userId AND server_revision > :cursor
      ORDER BY server_revision
      LIMIT :limit
    """.trimIndent(),
    mapOf("userId" to userId, "cursor" to cursor, "limit" to limit),
  ) { rs, _ -> StoredWord(rs.toWordDto(), rs.getLong("server_revision")) }

  fun findChangedSettings(userId: UUID, cursor: Long): StoredSettings? = jdbc.query(
    """
      SELECT * FROM user_settings
      WHERE user_id = :userId AND server_revision > :cursor
      ORDER BY server_revision
      LIMIT 1
    """.trimIndent(),
    mapOf("userId" to userId, "cursor" to cursor),
  ) { rs, _ -> StoredSettings(rs.toSettingsDto(), rs.getLong("server_revision")) }.firstOrNull()

  fun findChangedGameScores(userId: UUID, cursor: Long, limit: Int): List<StoredGameScore> = jdbc.query(
    """
      SELECT * FROM user_game_scores
      WHERE user_id = :userId AND server_revision > :cursor
      ORDER BY server_revision
      LIMIT :limit
    """.trimIndent(),
    mapOf("userId" to userId, "cursor" to cursor, "limit" to limit),
  ) { rs, _ -> StoredGameScore(rs.toGameScoreDto(), rs.getLong("server_revision")) }

  fun findAllWords(userId: UUID): List<StoredWord> = jdbc.query(
    "SELECT * FROM user_words WHERE user_id = :userId ORDER BY server_revision",
    mapOf("userId" to userId),
  ) { rs, _ -> StoredWord(rs.toWordDto(), rs.getLong("server_revision")) }

  fun findAllGameScores(userId: UUID): List<StoredGameScore> = jdbc.query(
    "SELECT * FROM user_game_scores WHERE user_id = :userId ORDER BY server_revision",
    mapOf("userId" to userId),
  ) { rs, _ -> StoredGameScore(rs.toGameScoreDto(), rs.getLong("server_revision")) }

  fun maxRevision(userId: UUID): Long = jdbc.queryForObject(
    """
      SELECT GREATEST(
        COALESCE((SELECT MAX(server_revision) FROM user_words WHERE user_id = :userId), 0),
        COALESCE((SELECT MAX(server_revision) FROM user_settings WHERE user_id = :userId), 0),
        COALESCE((SELECT MAX(server_revision) FROM user_game_scores WHERE user_id = :userId), 0)
      )
    """.trimIndent(),
    mapOf("userId" to userId),
    Long::class.java,
  ) ?: 0L

  private fun WordSyncDto.params(userId: UUID, revision: Long): Map<String, Any?> = mapOf(
    "userId" to userId,
    "uuid" to uuid,
    "english" to english,
    "russian" to russian,
    "repeatDays" to repeatDays,
    "addedDate" to addedDate,
    "isLearned" to isLearned,
    "stage" to stage,
    "nextReviewAt" to nextReviewAt,
    "lastReviewAt" to lastReviewAt,
    "correctStreak" to correctStreak,
    "isDeleted" to isDeleted,
    "updatedAt" to updatedAt,
    "serverRevision" to revision,
  )

  private fun ResultSet.toWordDto(): WordSyncDto = WordSyncDto(
    uuid = getString("uuid"),
    english = getString("english"),
    russian = getString("russian"),
    repeatDays = getInt("repeat_days"),
    addedDate = getString("added_date"),
    isLearned = getBoolean("is_learned"),
    stage = getInt("stage"),
    nextReviewAt = getString("next_review_at"),
    lastReviewAt = getString("last_review_at"),
    correctStreak = getInt("correct_streak"),
    isDeleted = getBoolean("is_deleted"),
    updatedAt = getLong("updated_at"),
  )

  private fun ResultSet.toSettingsDto(): SettingsSyncDto = SettingsSyncDto(
    language = getString("language"),
    theme = getString("theme"),
    enabledQuestionTypes = (getArray("enabled_question_types")?.array as? Array<*>)?.filterIsInstance<String>() ?: emptyList(),
    repetitionMode = getString("repetition_mode"),
    dailyReviewLimit = getObject("daily_review_limit") as? Int,
    newWordsPerDay = getObject("new_words_per_day") as? Int,
    remindersEnabled = getObject("reminders_enabled") as? Boolean,
    reminderHour = getObject("reminder_hour") as? Int,
    reminderMinute = getObject("reminder_minute") as? Int,
    updatedAt = getLong("updated_at"),
  )

  private fun ResultSet.toGameScoreDto(): GameScoreSyncDto = GameScoreSyncDto(
    pairCount = getInt("pair_count"),
    bestTimeMillis = getLong("best_time_millis"),
    updatedAt = getLong("updated_at"),
  )
}

data class StoredWord(val dto: WordSyncDto, val revision: Long)
data class StoredSettings(val dto: SettingsSyncDto, val revision: Long)
data class StoredGameScore(val dto: GameScoreSyncDto, val revision: Long)
