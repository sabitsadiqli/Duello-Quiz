package com.sabit.duelloquiz.core.network

import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DailyResult
import com.sabit.duelloquiz.core.model.DailyStart
import com.sabit.duelloquiz.core.model.DailyStatus
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.ProgressSummary
import com.sabit.duelloquiz.core.model.QuizResult
import com.sabit.duelloquiz.core.model.QuizSession
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.model.duel.MatchSummary

/**
 * The REST backend (`docs/api/openapi.yaml`). Every call sends `Authorization: Bearer <fresh
 * Firebase ID token>` and `Accept-Language`, and returns `:core:model` types; network DTOs never
 * leave this module. Errors are mapped by the stable `code` only (the `message` is never shown).
 * Requests time out after 15 s ([com.sabit.duelloquiz.core.model.DataError.Timeout]).
 */
interface DuelloNetworkDataSource {

    /** `GET /v1/me`: creates the profile on the first call (AUTH-06). */
    suspend fun getProfile(): DataResult<Profile>

    /** `PATCH /v1/me {displayName}` (sign-up name, OQ-011 / OQ-042). */
    suspend fun updateDisplayName(displayName: String): DataResult<Profile>

    /** `GET /v1/me/stats`: duel record and calibration counts (PROF-03, PROF-05). */
    suspend fun getStats(): DataResult<DuelStats>

    /** `GET /v1/me/matches?limit=`: recent duels, newest first (PROF-04, OQ-031). */
    suspend fun getMatches(limit: Int): DataResult<List<MatchSummary>>

    /** `DELETE /v1/me`: 204 and 404 both mean "deleted" (SET-05, idempotent). */
    suspend fun deleteAccount(): DataResult<Unit>

    /** `GET /v1/categories`: playable categories in server order (HOME-01). */
    suspend fun getCategories(): DataResult<List<Category>>

    /** `POST /v1/quiz/sessions`; also measures the clock offset from `serverNowEpochMs`. */
    suspend fun startQuizSession(categoryId: Int): DataResult<QuizSession>

    /** `POST /v1/quiz/sessions/{id}/answers`; [optionId] null = timeout (OQ-016). */
    suspend fun submitAnswer(
        sessionId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult>

    /** `POST /v1/quiz/sessions/{id}/finish` (idempotent). */
    suspend fun finishQuizSession(sessionId: String): DataResult<QuizResult>

    /** `GET /v1/daily`: today's state; measures the clock offset from `serverNowEpochMs`. */
    suspend fun getDaily(): DataResult<DailyStatus>

    /**
     * `POST /v1/daily/attempt`: 201 new / 200 resumed → [DailyStart.Started]; 409
     * `daily_already_played` → [DailyStart.AlreadyPlayed] with the stored result; 409
     * `daily_unavailable` → failure [com.sabit.duelloquiz.core.model.ApiErrorCode.DailyUnavailable].
     */
    suspend fun startDailyAttempt(): DataResult<DailyStart>

    /** `POST /v1/daily/attempt/{id}/answers`; same rules and codes as the quiz answer. */
    suspend fun submitDailyAnswer(
        attemptId: String,
        questionId: String,
        optionId: String?,
        elapsedMs: Long,
    ): DataResult<AnswerResult>

    /** `POST /v1/daily/attempt/{id}/finish` (idempotent). */
    suspend fun finishDailyAttempt(attemptId: String): DataResult<DailyResult>

    /** `GET /v1/me/progress` (PROG-03 … PROG-08). */
    suspend fun getProgress(): DataResult<ProgressSummary>

    /** `PUT /v1/me/push-tokens {token, locale}` (NOTIF-02); 204. */
    suspend fun registerPushToken(token: String, locale: String): DataResult<Unit>

    /** `DELETE /v1/me/push-tokens/{token}`; idempotent, always 204. */
    suspend fun deletePushToken(token: String): DataResult<Unit>

    /** `GET /v1/me/notification-settings` (NOTIF-06). */
    suspend fun getNotificationSettings(): DataResult<NotificationSettings>

    /** `PUT /v1/me/notification-settings`; returns the stored switches. */
    suspend fun updateNotificationSettings(settings: NotificationSettings): DataResult<NotificationSettings>
}
