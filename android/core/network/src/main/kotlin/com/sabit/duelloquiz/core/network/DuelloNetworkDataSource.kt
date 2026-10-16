package com.sabit.duelloquiz.core.network

import com.sabit.duelloquiz.core.model.AnswerResult
import com.sabit.duelloquiz.core.model.Category
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
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
}
