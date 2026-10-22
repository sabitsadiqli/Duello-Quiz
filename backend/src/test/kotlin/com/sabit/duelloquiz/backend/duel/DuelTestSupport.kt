package com.sabit.duelloquiz.backend.duel

import com.sabit.duelloquiz.backend.image.MediaUrls
import com.sabit.duelloquiz.backend.quiz.OptionRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Records everything the engine sends to one socket. */
class FakeConnection(override val language: String = "az") : DuelConnection {
    val received = mutableListOf<Outgoing>()
    var closedWith: Short? = null

    override fun send(message: Outgoing) {
        if (closedWith == null) received += message
    }

    override fun close(code: Short, reason: String) {
        closedWith = code
    }

    val messages: List<ServerMessage> get() = received.map { it.message }

    inline fun <reified T : ServerMessage> all(): List<T> = messages.filterIsInstance<T>()

    inline fun <reified T : ServerMessage> last(): T = all<T>().lastOrNull() ?: fail("No ${T::class.simpleName} in ${types()}")

    inline fun <reified T : ServerMessage> lastOrNull(): T? = all<T>().lastOrNull()

    fun errors(): List<ServerMessage.Error> = all()

    fun lastError(): ServerMessage.Error = errors().lastOrNull() ?: fail("No error in ${types()}")

    fun types(): List<String> = received.map { DuelJson.parseToJsonElement(it.encode()).let { e -> (e as kotlinx.serialization.json.JsonObject)["type"].toString() } }

    /** Match stream messages (seq > 0) must have seq 1, 2, 3, … without gaps or repeats. */
    fun assertSeqContiguous() {
        val seqs = received.map { it.seq }.filter { it > 0 }
        assertEquals((1L..seqs.size).toList(), seqs, "seq must be contiguous")
    }
}

/** In-memory [DuelStore] with the same Elo and idempotency rules as [DbDuelStore]. */
class FakeDuelStore(questionCount: Int = 12) : DuelStore {
    val questions: List<DuelQuestion> = (1..questionCount).map { n ->
        val id = UUID.nameUUIDFromBytes("q$n".toByteArray())
        DuelQuestion(
            id = id,
            text = mapOf("az" to "Sual $n?"),
            categoryName = mapOf("az" to "Coğrafiya"),
            image = null,
            options = (1..4).map { pos ->
                // The correct option sits at a different position per question.
                val correct = pos == (n % 4) + 1
                OptionRecord(
                    id = UUID.nameUUIDFromBytes("q$n-o$pos".toByteArray()),
                    text = mapOf("az" to if (correct) "right-$n" else "wrong-$n-$pos"),
                    isCorrect = correct,
                    position = pos,
                )
            },
        )
    }
    var activeQuestions = questionCount
    val users = LinkedHashMap<UUID, PlayerProfile>()
    val matches = LinkedHashMap<UUID, StoredMatch>()
    val rounds = mutableListOf<RoundRecord>()
    val ends = mutableListOf<MatchEnd>()
    var picks = 0
    /** Returned as the award per side by [endMatch] (the real store computes it in the same transaction). */
    var progress: Map<Side, com.sabit.duelloquiz.backend.progress.ProgressDto> = emptyMap()

    fun addUser(name: String, rating: Int = 1000): UUID {
        val id = UUID.nameUUIDFromBytes(name.toByteArray())
        users[id] = PlayerProfile(id, name, null, rating)
        return id
    }

    override suspend fun activeQuestionCount() = activeQuestions

    override suspend fun pickQuestions(count: Int): List<DuelQuestion> {
        picks++
        // Rotate so consecutive matches get a different order (still distinct within a match).
        return (questions.drop(picks % questions.size) + questions.take(picks % questions.size)).take(count)
    }

    override suspend fun profiles(userIds: Collection<UUID>) = users.filterKeys { it in userIds }

    override suspend fun createMatch(matchId: UUID, playerA: UUID, playerB: UUID, startedAtMs: Long) {
        matches[matchId] = StoredMatch(matchId, playerA, playerB, MatchStatus.ACTIVE, null, null, null, 0, 0, null, emptyList())
    }

    override suspend fun saveRound(round: RoundRecord) {
        assertTrue(rounds.none { it.matchId == round.matchId && it.roundNo == round.roundNo }, "round saved twice")
        assertTrue(rounds.none { it.matchId == round.matchId && it.questionId == round.questionId }, "question repeated in a match")
        rounds += round
    }

    override suspend fun endMatch(end: MatchEnd, ratingAtStartA: Int, ratingAtStartB: Int): MatchEndResult {
        val m = matches.getValue(end.matchId)
        if (m.status != MatchStatus.ACTIVE) return MatchEndResult(m.ratings!!)
        ends += end
        val a = users[m.playerA]?.rating ?: ratingAtStartA
        val b = users[m.playerB]?.rating ?: ratingAtStartB
        val ratings = if (end.status == MatchStatus.FINISHED) {
            val s = when (end.winnerSide) {
                Side.A -> 1.0
                Side.B -> 0.0
                null -> 0.5
            }
            val change = DuelRules.eloChangeA(a, b, s)
            RatingUpdate(a, a + change, b, b - change)
        } else {
            RatingUpdate(a, a, b, b)
        }
        if (end.status == MatchStatus.FINISHED) {
            m.playerA?.let { id -> users[id] = users.getValue(id).copy(rating = ratings.aAfter) }
            m.playerB?.let { id -> users[id] = users.getValue(id).copy(rating = ratings.bAfter) }
        }
        matches[end.matchId] = m.copy(
            status = end.status, endReason = end.endReason, winnerSide = end.winnerSide,
            forfeitedSide = end.forfeitedSide, scoreA = end.scoreA, scoreB = end.scoreB, ratings = ratings,
            forfeitCause = end.forfeitCause,
        )
        return MatchEndResult(ratings, progress)
    }

    override suspend fun abandonActiveMatches(atMs: Long): Int {
        val active = matches.values.filter { it.status == MatchStatus.ACTIVE }
        active.forEach { matches[it.id] = it.copy(status = MatchStatus.ABANDONED, endReason = EndReasons.ABANDONED) }
        return active.size
    }

    override suspend fun findMatch(matchId: UUID): StoredMatch? = matches[matchId]?.let { m ->
        m.copy(
            rounds = rounds.filter { it.matchId == matchId }.map { r ->
                StoredRound(r.roundNo, r.suddenDeath, r.answers.associateBy { it.side })
            },
        )
    }
}

/**
 * Drives a [DuelService] on the virtual time of [TestScope]: the duel clock is
 * `BASE + currentTime`, so `deadlineEpochMs − BASE` is the virtual time of a deadline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DuelHarness(private val test: TestScope, val config: DuelConfig = DuelConfig(), questionCount: Int = 12) {
    val store = FakeDuelStore(questionCount)
    val clock = DuelClock { BASE + test.testScheduler.currentTime }
    val queue = InMemoryMatchmakingQueue()
    val service = DuelService(test.backgroundScope, config, clock, store, queue, MediaUrls("https://media.example.test/media"))
    val alice = store.addUser("Alice")
    val bob = store.addUser("Bob")
    val carol = store.addUser("Carol")

    fun now() = clock.nowMs()

    /** Advances virtual time to [epochMs] and runs everything due at that instant. */
    fun advanceTo(epochMs: Long) {
        val delta = epochMs - now()
        require(delta >= 0) { "cannot go back in time ($delta ms)" }
        test.testScheduler.advanceTimeBy(delta)
        test.testScheduler.runCurrent()
    }

    fun advanceBy(ms: Long) = advanceTo(now() + ms)

    fun runCurrent() = test.testScheduler.runCurrent()

    suspend fun connect(user: UUID, language: String = "az"): FakeConnection {
        val c = FakeConnection(language)
        service.connect(user, c)
        runCurrent()
        return c
    }

    suspend fun send(user: UUID, connection: FakeConnection, message: ClientMessage) {
        service.handle(user, connection, message, now())
        runCurrent()
    }

    /** Connects Alice and Bob, queues both (Alice first) and returns their sockets after `match.found`. */
    suspend fun startMatch(): Pair<FakeConnection, FakeConnection> {
        val a = connect(alice)
        val b = connect(bob)
        send(alice, a, ClientMessage.QueueJoin)
        send(bob, b, ClientMessage.QueueJoin)
        a.last<ServerMessage.MatchFound>()
        b.last<ServerMessage.MatchFound>()
        return a to b
    }

    fun matchId(c: FakeConnection) = c.last<ServerMessage.MatchFound>().matchId

    /** Moves to the end of the current question phase of [c] (the confidence or answer phase starts). */
    fun finishQuestionPhase(c: FakeConnection) {
        advanceTo(c.last<ServerMessage.RoundQuestion>().deadlineEpochMs)
    }

    /** Moves past the intro and the question phase of round 1. */
    fun toFirstConfidence(c: FakeConnection) {
        advanceTo(c.last<ServerMessage.MatchFound>().deadlineEpochMs)
        finishQuestionPhase(c)
    }

    fun rightOption(c: FakeConnection): String = c.last<ServerMessage.RoundOptions>().options.first { it.text.startsWith("right") }.id

    fun wrongOption(c: FakeConnection): String = c.last<ServerMessage.RoundOptions>().options.first { it.text.startsWith("wrong") }.id

    suspend fun bet(user: UUID, c: FakeConnection, bet: Int?, roundNo: Int = currentRound(c)) =
        send(user, c, ClientMessage.RoundBet(matchId(c), roundNo, bet))

    suspend fun answer(user: UUID, c: FakeConnection, optionId: String, roundNo: Int = currentRound(c)) =
        send(user, c, ClientMessage.RoundAnswer(matchId(c), roundNo, optionId))

    fun currentRound(c: FakeConnection) = c.last<ServerMessage.RoundQuestion>().roundNo

    /**
     * Plays one regular round from the start of its question phase: both pick (null =
     * no pick), both answer (true = right, false = wrong, null = none) after [answerDelayMs]
     * of the answer phase, then waits for the result.
     */
    suspend fun playRound(
        a: FakeConnection, betA: Int?, rightA: Boolean?,
        b: FakeConnection, betB: Int?, rightB: Boolean?,
        answerDelayMs: Long = 0,
    ): Pair<ServerMessage.RoundResult, ServerMessage.RoundResult> {
        val roundNo = currentRound(a)
        finishQuestionPhase(a)
        if (!a.last<ServerMessage.RoundQuestion>().suddenDeath) {
            if (betA != null) bet(alice, a, betA, roundNo)
            if (betB != null) bet(bob, b, betB, roundNo)
            if (a.lastOrNull<ServerMessage.RoundOptions>()?.roundNo != roundNo) {
                advanceTo(a.last<ServerMessage.RoundConfidence>().deadlineEpochMs + config.graceMs + 1)
            }
        }
        val options = a.last<ServerMessage.RoundOptions>()
        assertEquals(roundNo, options.roundNo)
        advanceTo(options.deadlineEpochMs - config.answerMs + answerDelayMs)
        rightA?.let { answer(alice, a, if (it) rightOption(a) else wrongOption(a), roundNo) }
        rightB?.let { answer(bob, b, if (it) rightOption(b) else wrongOption(b), roundNo) }
        if (a.lastOrNull<ServerMessage.RoundResult>()?.roundNo != roundNo) {
            advanceTo(options.deadlineEpochMs + config.graceMs + 1)
        }
        val ra = a.last<ServerMessage.RoundResult>()
        val rb = b.last<ServerMessage.RoundResult>()
        assertEquals(roundNo, ra.roundNo)
        // Next question starts when the result phase ends.
        if (ra.next != NextStep.END) advanceTo(ra.deadlineEpochMs)
        return ra to rb
    }

    companion object {
        const val BASE = 1_800_000_000_000L
    }
}
