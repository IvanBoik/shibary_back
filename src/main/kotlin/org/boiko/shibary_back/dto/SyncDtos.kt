package org.boiko.shibary_back.dto

data class SyncRequest(
  val cursor: String? = null,
  val clientChanges: SyncChangesDto = SyncChangesDto(),
)

data class SyncResponse(
  val cursor: String,
  val serverChanges: SyncChangesDto,
  val applied: AppliedSyncDto? = null,
  val conflicts: List<SyncConflictDto>? = null,
)

data class SyncChangesDto(
  val words: List<WordSyncDto> = emptyList(),
  val settings: SettingsSyncDto? = null,
  val gameScores: List<GameScoreSyncDto> = emptyList(),
)

data class WordSyncDto(
  val uuid: String,
  val english: String,
  val russian: String,
  val repeatDays: Int,
  val addedDate: String,
  val isLearned: Boolean,
  val stage: Int,
  val nextReviewAt: String,
  val lastReviewAt: String,
  val correctStreak: Int,
  val isDeleted: Boolean,
  val updatedAt: Long,
)

data class SettingsSyncDto(
  val language: String? = null,
  val theme: String? = null,
  val enabledQuestionTypes: List<String> = emptyList(),
  val repetitionMode: String? = null,
  val dailyReviewLimit: Int? = null,
  val newWordsPerDay: Int? = null,
  val remindersEnabled: Boolean? = null,
  val reminderHour: Int? = null,
  val reminderMinute: Int? = null,
  val updatedAt: Long,
)

data class GameScoreSyncDto(
  val pairCount: Int,
  val bestTimeMillis: Long,
  val updatedAt: Long,
)

data class AppliedSyncDto(
  val words: Int,
  val gameScores: Int,
  val settingsAccepted: Boolean,
)

data class SyncConflictDto(
  val type: String,
  val uuid: String,
  val resolution: String,
)
