package com.sabit.duelloquiz.backend.user

import com.sabit.duelloquiz.backend.auth.AuthAccountDeleter
import com.sabit.duelloquiz.backend.auth.AuthProviderException
import com.sabit.duelloquiz.backend.auth.AuthUser
import com.sabit.duelloquiz.backend.common.ApiException
import com.sabit.duelloquiz.backend.common.ErrorCodes
import io.ktor.http.HttpStatusCode
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** Runs before an account is deleted (e.g. the duel forfeits a running match). */
fun interface AccountDeletionListener {
    suspend fun beforeAccountDeletion(userId: UUID)
}

class UserService(
    private val repository: UserRepository,
    private val namePolicy: DisplayNamePolicy,
    private val accountDeleter: AuthAccountDeleter,
    private val clock: Clock,
    private val deletionListeners: List<AccountDeletionListener> = emptyList(),
) {
    private val log = LoggerFactory.getLogger(UserService::class.java)

    /** Returns the caller's profile, creating it on the first call (AUTH-06). */
    suspend fun getOrCreate(auth: AuthUser): UserRecord =
        repository.findByFirebaseUid(auth.firebaseUid)
            ?: repository.createIfAbsent(
                firebaseUid = auth.firebaseUid,
                displayName = namePolicy.initialName(auth.name),
                avatarUrl = auth.picture?.takeIf { it.startsWith("https://") },
                now = clock.instant(),
            )

    suspend fun updateDisplayName(auth: AuthUser, rawName: String): UserRecord {
        val name = when (val result = namePolicy.check(rawName)) {
            is DisplayNamePolicy.Result.Valid -> result.name
            DisplayNamePolicy.Result.Invalid -> throw ApiException(
                HttpStatusCode.BadRequest, ErrorCodes.INVALID_DISPLAY_NAME,
                "Display name must be 2-20 allowed characters",
            )
            DisplayNamePolicy.Result.NotAllowed -> throw ApiException(
                HttpStatusCode.BadRequest, ErrorCodes.DISPLAY_NAME_NOT_ALLOWED, "Display name is not allowed",
            )
        }
        val user = getOrCreate(auth)
        if (user.displayName == name) return user
        return repository.updateDisplayName(user.id, name, clock.instant())
    }

    /**
     * Deletes database data first, then the Firebase user, so a failed Firebase call can
     * be retried with the same (still valid) token. Never creates a profile.
     */
    suspend fun deleteAccount(auth: AuthUser) {
        try {
            deleteAccount(auth.firebaseUid)
        } catch (e: AuthProviderException) {
            throw ApiException(HttpStatusCode.BadGateway, ErrorCodes.AUTH_PROVIDER_ERROR, "Firebase user could not be deleted")
        }
    }

    /**
     * The one account deletion (SET-05/SET-06, OQ-014): used by `DELETE /v1/me` and by the
     * admin CLI tasks `delete-user` and `prune-orphans` (OQ-102). Idempotent: an unknown
     * uid or an already deleted Firebase user is not an error. Logs only the internal id.
     * @return the internal id of the deleted profile, or null if there was none.
     * @throws AuthProviderException if the Firebase user could not be deleted (the
     *   database data is already gone; a retry completes the deletion).
     */
    suspend fun deleteAccount(firebaseUid: String): UUID? {
        val user = repository.findByFirebaseUid(firebaseUid)
        if (user != null) deletionListeners.forEach { it.beforeAccountDeletion(user.id) }
        repository.deleteByFirebaseUid(firebaseUid)
        try {
            accountDeleter.deleteAccount(firebaseUid)
        } catch (e: AuthProviderException) {
            log.error("account_deletion_firebase_failed userId={}", user?.id, e)
            throw e
        }
        log.info("account_deleted userId={}", user?.id)
        return user?.id
    }

    suspend fun stats(auth: AuthUser): StatsResponse {
        val user = getOrCreate(auth)
        val matches = repository.matchesOf(user.id, listOf(UserRepository.MATCH_FINISHED), limit = null)
        var wins = 0
        var losses = 0
        var draws = 0
        for (m in matches) {
            when (m.winnerSide) {
                null -> draws++
                m.ownSide -> wins++
                else -> losses++
            }
        }
        val rows = repository.calibrationRows(user.id)
        val calibration = (1..3).map { level ->
            val atLevel = rows.filter { it.bet == level }
            CalibrationLevel(level = level, picks = atLevel.size, correct = atLevel.count { it.isCorrect })
        }
        return StatsResponse(Record(wins, losses, draws, wins + losses + draws), calibration)
    }

    suspend fun recentMatches(auth: AuthUser, limit: Int): MatchHistoryResponse {
        val user = getOrCreate(auth)
        val matches = repository.matchesOf(
            user.id,
            listOf(UserRepository.MATCH_FINISHED, UserRepository.MATCH_ABANDONED),
            limit,
        )
        val opponents = repository.findDisplayInfo(matches.mapNotNull { it.opponentId }.toSet())
        return MatchHistoryResponse(
            matches.map { m ->
                val opponent = m.opponentId?.let { opponents[it] }
                MatchSummary(
                    id = m.id.toString(),
                    opponent = OpponentDto(
                        displayName = opponent?.first,
                        avatarUrl = opponent?.second,
                        deleted = opponent == null,
                    ),
                    result = when {
                        m.status == UserRepository.MATCH_ABANDONED -> "abandoned"
                        m.winnerSide == null -> "draw"
                        m.winnerSide == m.ownSide -> "win"
                        else -> "loss"
                    },
                    endReason = m.endReason,
                    forfeitCause = m.forfeitCause,
                    scoreOwn = m.scoreOwn,
                    scoreOpponent = m.scoreOpponent,
                    ratingChange = if (m.ratingBefore != null && m.ratingAfter != null) m.ratingAfter - m.ratingBefore else 0,
                    finishedAt = m.finishedAt.toString(),
                )
            },
        )
    }
}
