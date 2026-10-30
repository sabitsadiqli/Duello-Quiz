package com.sabit.duelloquiz.core.duel

import com.sabit.duelloquiz.core.datastore.DuelMatchDataSource
import com.sabit.duelloquiz.core.model.duel.DuelPlayer
import com.sabit.duelloquiz.core.model.duel.OpponentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The running match of this device (DUEL-16): its id and the players of `match.found`, so the
 * result screen can still show names after a process death (`match.result` carries no player
 * data).
 */
data class ActiveDuel(
    val matchId: String,
    val you: DuelPlayer?,
    val opponent: DuelPlayer?,
    /** v1.2: from `match.found` / `match.state` (the only bot signal, never the name). */
    val opponentType: OpponentType = OpponentType.HUMAN,
)

/** Persists [ActiveDuel] and the "left while offline" marker (OQ-058). */
interface ActiveDuelStore {
    /** Id of the stored running match; home uses it to resume after a process death. */
    val activeMatchId: Flow<String?>

    suspend fun activeDuel(): ActiveDuel?

    suspend fun setActiveDuel(duel: ActiveDuel?)

    suspend fun leftMatchId(): String?

    suspend fun setLeftMatchId(matchId: String?)
}

@Serializable
private data class StoredPlayer(val displayName: String, val avatarUrl: String? = null, val rating: Int? = null)

@Serializable
private data class StoredDuel(
    val matchId: String,
    val you: StoredPlayer? = null,
    val opponent: StoredPlayer? = null,
    val bot: Boolean = false,
)

@Singleton
internal class DataStoreActiveDuelStore @Inject constructor(
    private val dataSource: DuelMatchDataSource,
) : ActiveDuelStore {

    private val json = Json { ignoreUnknownKeys = true }

    override val activeMatchId: Flow<String?> = dataSource.activeDuel.map { decode(it)?.matchId }

    override suspend fun activeDuel(): ActiveDuel? = decode(dataSource.activeDuel.first())

    override suspend fun setActiveDuel(duel: ActiveDuel?) {
        dataSource.setActiveDuel(
            duel?.let {
                json.encodeToString(
                    StoredDuel.serializer(),
                    StoredDuel(it.matchId, it.you?.stored(), it.opponent?.stored(), it.opponentType == OpponentType.BOT),
                )
            },
        )
    }

    override suspend fun leftMatchId(): String? = dataSource.leftMatchId.first()

    override suspend fun setLeftMatchId(matchId: String?) = dataSource.setLeftMatchId(matchId)

    /** An unreadable value reads as "no active duel" (never a crash). */
    private fun decode(value: String?): ActiveDuel? = value?.let {
        runCatching { json.decodeFromString(StoredDuel.serializer(), it) }.getOrNull()
    }?.let {
        ActiveDuel(
            it.matchId,
            it.you?.player(),
            it.opponent?.player(),
            if (it.bot) OpponentType.BOT else OpponentType.HUMAN,
        )
    }

    private fun DuelPlayer.stored() = StoredPlayer(displayName, avatarUrl, rating)

    private fun StoredPlayer.player() = DuelPlayer(displayName, avatarUrl, rating)
}
