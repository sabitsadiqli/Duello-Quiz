package com.sabit.duelloquiz.feature.exams.attempt

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.data.exam.ExamAttemptSnapshot
import com.sabit.duelloquiz.core.data.exam.ExamDownloadState
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.ExamSubmitOutcome
import com.sabit.duelloquiz.core.data.exam.ExamSyncOutcome
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.data.exam.LocalListening
import com.sabit.duelloquiz.core.data.util.NetworkMonitor
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.exam.ExamGroupKind
import com.sabit.duelloquiz.core.model.exam.ExamPackage
import com.sabit.duelloquiz.core.model.exam.ExamSubmitTrigger
import com.sabit.duelloquiz.core.model.exam.ExamTaskType
import com.sabit.duelloquiz.feature.exams.audio.AudioSnapshot
import com.sabit.duelloquiz.feature.exams.audio.ExamAudioEngine
import com.sabit.duelloquiz.feature.exams.audio.ListeningPlayState
import com.sabit.duelloquiz.feature.exams.common.ExamFormat
import com.sabit.duelloquiz.feature.exams.navigation.ExamArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The attempt (exam-attempt.md, EXAM-04 … EXAM-14, EXAM-27 foreground state). The countdown is
 * `endsAtEpochMs` minus the server-offset clock, re-read every second from the wall clock (never a
 * local fixed timer, OQ-D41); at 0 every input stops and the attempt auto-submits. Every change is
 * a new revision in Room first, then synced (offline queue). Listening follows
 * [ListeningPlayState]. Nothing here knows a correct answer (EXAM-21).
 */
@HiltViewModel
class ExamAttemptViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExamRepository,
    private val networkMonitor: NetworkMonitor,
    private val audio: ExamAudioEngine,
    private val clock: WallClock,
    private val telemetry: Telemetry,
) : ViewModel() {

    private val attemptId = ExamArgs.attemptId(savedStateHandle)

    private val _state = MutableStateFlow(ExamAttemptUiState())
    val uiState: StateFlow<ExamAttemptUiState> = _state.asStateFlow()
    private val _events = Channel<ExamAttemptEvent>(Channel.BUFFERED)
    val events: Flow<ExamAttemptEvent> = _events.receiveAsFlow()

    private var pkg: ExamPackage? = null
    private var attempt: LocalExamAttempt? = null
    private var snapshot: ExamAttemptSnapshot? = null
    private val drafts = mutableMapOf<String, String>()
    private val textJobs = mutableMapOf<String, Job>()
    private var syncJob: Job? = null
    private var tickerJob: Job? = null
    private var listening: ListeningPlayState? = null
    private var listeningGroupId: String? = null
    private var lastListeningSaveMs = 0L
    private var audioRetried = false
    private var audioBusy = false
    private var foreground = true
    private var lastTickRemaining: Long? = null
    private var timeUpStarted = false
    private var restoredPosition = false
    private val visitedLocal = mutableSetOf<String>()
    private val online = networkMonitor.isOnline.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val local = repository.attemptSnapshot(attemptId).filterNotNull().first()
        snapshot = local
        attempt = local.attempt
        visitedLocal += local.attempt.visitedGroupIds
        _state.update {
            it.copy(
                variantNumber = local.attempt.variantNumber,
                remainingMs = local.attempt.remainingMs(clock.nowMs()),
                listeningSkipped = local.attempt.skipListening,
            )
        }
        startTicker()
        when (local.attempt.phase) {
            LocalAttemptPhase.SUBMITTED -> {
                _events.send(ExamAttemptEvent.OpenResult(attemptId))
                return
            }
            LocalAttemptPhase.CLOSED -> {
                _state.update { it.copy(dialog = AttemptDialog.Closed) }
                return
            }
            LocalAttemptPhase.SUBMIT_PENDING -> Unit
            LocalAttemptPhase.RUNNING -> Unit
        }
        val loaded = repository.localPackage(local.attempt.variantId, local.attempt.version)
        val offsets = repository.passageOffsets(attemptId)
        _state.update { it.copy(passageOffsets = offsets) }
        if (loaded == null) {
            observePackageDownload(local.attempt)
        } else {
            onPackageReady(loaded)
        }
        observeSnapshot()
        observeNetwork()
        if (local.attempt.phase == LocalAttemptPhase.SUBMIT_PENDING) {
            val trigger = local.attempt.submitTrigger ?: ExamSubmitTrigger.USER
            timeUpStarted = true
            _state.update {
                it.copy(phase = if (trigger == ExamSubmitTrigger.DEADLINE) AttemptPhase.TimeUp(sending = true) else AttemptPhase.Submitting)
            }
            submit(trigger)
        } else if (local.attempt.isTimeUp(clock.nowMs())) {
            onTimeUp()
        } else {
            // Answers of another device (EXAM-13) when online; silent otherwise.
            val merged = repository.refreshAttempt(attemptId)
            if (merged is DataResult.Success && merged.value) _events.send(ExamAttemptEvent.MergedFromOtherDevice)
        }
    }

    private fun onPackageReady(loaded: ExamPackage) {
        pkg = loaded
        val current = attempt ?: return
        val restore = !restoredPosition
        restoredPosition = true
        if (current.phase == LocalAttemptPhase.RUNNING && !current.isTimeUp(clock.nowMs())) {
            _state.update { it.copy(phase = AttemptPhase.Active) }
        }
        initListening(loaded, current)
        rebuild()
        if (restore) {
            val index = _state.value.pages.indexOfFirst { it.id == current.currentTaskId }.coerceAtLeast(0)
            moveTo(index, restore = true)
        }
    }

    private fun observePackageDownload(local: LocalExamAttempt) {
        viewModelScope.launch {
            val sizeMb = repository.cachedVariant(local.variantId).first()?.packageBytes?.let(ExamFormat::megabytes).orEmpty()
            repository.downloadState(local.variantId).collect { download ->
                if (pkg != null) return@collect
                if (download is ExamDownloadState.Done) {
                    repository.localPackage(local.variantId, local.version)?.let { onPackageReady(it) }
                } else if (_state.value.phase is AttemptPhase.Opening || _state.value.phase is AttemptPhase.PackageMissing) {
                    _state.update { it.copy(phase = AttemptPhase.PackageMissing(download, sizeMb)) }
                }
            }
        }
    }

    private fun observeSnapshot() {
        viewModelScope.launch {
            repository.attemptSnapshot(attemptId).collect { next ->
                if (next == null) return@collect
                snapshot = next
                attempt = next.attempt
                if (next.attempt.phase == LocalAttemptPhase.CLOSED && _state.value.dialog != AttemptDialog.Closed) {
                    audio.pause()
                    _state.update { it.copy(dialog = AttemptDialog.Closed, navigatorOpen = false) }
                }
                rebuild()
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            var wasOnline = true
            online.collect { isOnline ->
                _state.update { it.copy(offline = !isOnline) }
                if (isOnline && !wasOnline) scheduleSync(immediate = true)
                wasOnline = isOnline
            }
        }
    }

    // ------------------------------------------------------------------ timer

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                tick()
                delay(TICK_MS - clock.nowMs() % TICK_MS)
            }
        }
    }

    private fun tick() {
        val current = attempt ?: return
        val remaining = current.remainingMs(clock.nowMs())
        _state.update { it.copy(remainingMs = remaining, durationMs = (current.endsAtEpochMs - current.startedAtEpochMs).coerceAtLeast(1)) }
        val previous = lastTickRemaining
        lastTickRemaining = remaining
        if (foreground && previous != null && previous - remaining <= NOTICE_JUMP_MS && _state.value.phase == AttemptPhase.Active) {
            NOTICE_MINUTES.firstOrNull { minutes -> previous > minutes * MINUTE_MS && remaining <= minutes * MINUTE_MS }
                ?.let { _events.trySend(ExamAttemptEvent.TimeNotice(it)) }
        }
        if (remaining <= 0 && current.phase == LocalAttemptPhase.RUNNING) onTimeUp()
    }

    /** G: everything stops at once, dialogs and the navigator close, the queue flushes and submits. */
    private fun onTimeUp() {
        if (timeUpStarted) return
        timeUpStarted = true
        audio.pause()
        persistListening(force = true)
        flushDrafts()
        _state.update { it.copy(phase = AttemptPhase.TimeUp(sending = true), dialog = null, navigatorOpen = false) }
        _events.trySend(ExamAttemptEvent.TimeUp)
        viewModelScope.launch { submit(ExamSubmitTrigger.DEADLINE) }
    }

    private suspend fun submit(trigger: ExamSubmitTrigger) {
        flushDraftsNow()
        when (val outcome = repository.submit(attemptId, trigger)) {
            is ExamSubmitOutcome.Submitted -> {
                stopAudio()
                val counts = _state.value.counts
                telemetry.logEvent(
                    "exam_submit",
                    mapOf(
                        "exam_type" to (attempt?.templateId ?: ""),
                        "mode" to outcome.result.mode.wire,
                        "trigger" to outcome.result.submitTrigger.wire,
                        "answered_count" to counts.answered,
                        "flagged_count" to counts.flagged,
                        "offline_at_submit" to !online.value,
                    ),
                )
                _events.send(ExamAttemptEvent.OpenResult(attemptId))
            }
            ExamSubmitOutcome.Queued, is ExamSubmitOutcome.Failed -> {
                stopAudio()
                _state.update {
                    it.copy(phase = if (trigger == ExamSubmitTrigger.DEADLINE) AttemptPhase.TimeUp(sending = false) else AttemptPhase.SubmitOffline)
                }
            }
            ExamSubmitOutcome.Closed -> {
                stopAudio()
                _state.update { it.copy(dialog = AttemptDialog.Closed) }
            }
        }
    }

    // ------------------------------------------------------------------ actions

    fun onAction(action: ExamAttemptAction) {
        when (action) {
            is ExamAttemptAction.PageChanged -> moveTo(action.index, fromPager = true)
            ExamAttemptAction.Next -> {
                val state = _state.value
                if (state.isLastPage) finishClicked() else moveTo(state.currentIndex + 1)
            }
            ExamAttemptAction.Previous -> moveTo(_state.value.currentIndex - 1)
            is ExamAttemptAction.GoTo -> {
                _state.update { it.copy(navigatorOpen = false) }
                moveTo(action.index)
            }
            is ExamAttemptAction.SelectSegment -> _state.update { it.copy(segment = action.segment) }
            is ExamAttemptAction.SelectOption -> answer { repository.selectOption(attemptId, action.taskId, action.optionId) }
            is ExamAttemptAction.ClearAnswer -> answer { repository.selectOption(attemptId, action.taskId, null) }
            is ExamAttemptAction.TextChanged -> textChanged(action.taskId, action.text)
            is ExamAttemptAction.ToggleFlag -> toggleFlag(action.taskId)
            is ExamAttemptAction.PassageScrolled -> {
                _state.update { it.copy(passageOffsets = it.passageOffsets + (action.groupId to action.charOffset)) }
                viewModelScope.launch { repository.savePassageOffset(attemptId, action.groupId, action.charOffset) }
            }
            ExamAttemptAction.PlayPause -> playPause()
            ExamAttemptAction.RetryAudio -> retryAudio()
            ExamAttemptAction.OpenNavigator -> if (!_state.value.readOnly) _state.update { it.copy(navigatorOpen = true) }
            ExamAttemptAction.CloseNavigator -> _state.update { it.copy(navigatorOpen = false) }
            ExamAttemptAction.CloseClicked -> if (_state.value.phase == AttemptPhase.Active) {
                _state.update { it.copy(dialog = AttemptDialog.Leave(ExamFormat.minutesLeft(it.remainingMs))) }
            }
            ExamAttemptAction.LeaveLater -> {
                _state.update { it.copy(dialog = null) }
                flushDrafts()
                persistListening(force = true)
                scheduleSync(immediate = true)
                _events.trySend(ExamAttemptEvent.Leave)
            }
            ExamAttemptAction.FinishClicked -> finishClicked()
            ExamAttemptAction.ConfirmSubmit -> confirmSubmit()
            ExamAttemptAction.DismissDialog -> _state.update { if (it.dialog == AttemptDialog.Closed) it else it.copy(dialog = null) }
            ExamAttemptAction.OpenResult -> _events.trySend(ExamAttemptEvent.OpenResult(attemptId))
            ExamAttemptAction.BackToCatalogue -> _events.trySend(ExamAttemptEvent.Leave)
            ExamAttemptAction.DownloadPackage -> attempt?.let { a ->
                viewModelScope.launch {
                    val bytes = repository.cachedVariant(a.variantId).first()?.packageBytes ?: 0L
                    repository.startDownload(a.variantId, a.version, bytes)
                }
            }
            ExamAttemptAction.TimerTapped -> _events.trySend(ExamAttemptEvent.AnnounceTime(_state.value.remainingMs))
            is ExamAttemptAction.Foreground -> onForeground(action.visible)
        }
    }

    private fun finishClicked() {
        if (_state.value.phase != AttemptPhase.Active) return
        _state.update { it.copy(dialog = AttemptDialog.Submit(it.counts), navigatorOpen = false) }
    }

    private fun confirmSubmit() {
        if (_state.value.phase != AttemptPhase.Active) return
        audio.pause()
        persistListening(force = true)
        flushDrafts()
        _state.update { it.copy(dialog = null, phase = AttemptPhase.Submitting, navigatorOpen = false) }
        viewModelScope.launch { submit(ExamSubmitTrigger.USER) }
    }

    private fun onForeground(visible: Boolean) {
        foreground = visible
        // Back from the background: no stale notices for thresholds passed meanwhile (EXAM-04).
        lastTickRemaining = null
        if (visible) tick()
    }

    private fun answer(write: suspend () -> Unit) {
        if (_state.value.phase != AttemptPhase.Active) return
        viewModelScope.launch {
            write()
            scheduleSync(immediate = false)
        }
    }

    private fun textChanged(taskId: String, text: String) {
        if (_state.value.phase != AttemptPhase.Active) return
        val limited = text.take(_state.value.maxTextLength)
        drafts[taskId] = limited
        rebuild()
        // Saved locally on every change, debounced 500 ms (components.md §43).
        textJobs[taskId]?.cancel()
        textJobs[taskId] = viewModelScope.launch {
            delay(TEXT_DEBOUNCE_MS)
            repository.setText(attemptId, taskId, limited)
            scheduleSync(immediate = false)
        }
    }

    private fun flushDrafts() {
        viewModelScope.launch { flushDraftsNow() }
    }

    private suspend fun flushDraftsNow() {
        val pending = textJobs.filterValues { it.isActive }.keys.toList()
        pending.forEach { taskId ->
            textJobs.remove(taskId)?.cancel()
            drafts[taskId]?.let { repository.setText(attemptId, taskId, it) }
        }
    }

    private fun toggleFlag(taskId: String) {
        val task = _state.value.pages.firstOrNull { it.id == taskId } ?: return
        val flagged = !task.flagged
        answer { repository.setFlag(attemptId, taskId, flagged) }
        _events.trySend(ExamAttemptEvent.FlagChanged(flagged))
    }

    private fun scheduleSync(immediate: Boolean) {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            if (!immediate) delay(SYNC_DEBOUNCE_MS)
            if (repository.sync(attemptId) == ExamSyncOutcome.Closed) {
                audio.pause()
                _state.update { it.copy(dialog = AttemptDialog.Closed, navigatorOpen = false) }
            }
        }
    }

    /**
     * Moves to page [index]. EXAM-09: the first visit of a reading group opens "Mətn", every other
     * move lands on "Tapşırıq".
     */
    private fun moveTo(index: Int, fromPager: Boolean = false, restore: Boolean = false) {
        val pages = _state.value.pages
        if (pages.isEmpty()) return
        val target = index.coerceIn(0, pages.lastIndex)
        if (target == _state.value.currentIndex && fromPager && !restore) return
        val task = pages[target]
        val groupId = task.groupId
        val firstVisit = task.groupKind == ExamGroupKind.READING && groupId != null && groupId !in visitedLocal
        if (firstVisit && groupId != null) visitedLocal += groupId
        _state.update { it.copy(currentIndex = target, segment = if (firstVisit) TaskSegment.PASSAGE else TaskSegment.TASK) }
        viewModelScope.launch {
            repository.setCurrentTask(attemptId, task.id)
            if (firstVisit && groupId != null) repository.markGroupVisited(attemptId, groupId)
        }
        ensureAudioLoaded()
    }

    // ------------------------------------------------------------------ listening (EXAM-10)

    private fun initListening(loaded: ExamPackage, current: LocalExamAttempt) {
        if (current.skipListening) return
        val group = loaded.groups.firstOrNull { it.kind == ExamGroupKind.LISTENING && it.audio != null } ?: return
        val stored = snapshot?.listening?.get(group.id)
        listeningGroupId = group.id
        listening = ListeningPlayState.restore(
            maxPlays = loaded.listeningMaxPlays,
            countsAfterMs = loaded.listeningPlayCountsAfterMs,
            playsUsed = stored?.playsUsed ?: 0,
            playInProgress = stored?.playInProgress == true,
            positionMs = stored?.positionMs ?: 0,
            audioFailed = stored?.audioFailed == true,
        )
        viewModelScope.launch { audio.state.collect { onAudio(it) } }
    }

    private fun audioKey(groupId: String) = "$attemptId:$groupId"

    private fun ensureAudioLoaded() {
        val groupId = listeningGroupId ?: return
        val state = listening ?: return
        if (audio.state.value.key == audioKey(groupId)) return
        val group = pkg?.groups?.firstOrNull { it.id == groupId } ?: return
        val media = group.audio ?: return
        if (state.audioFailed) return
        viewModelScope.launch {
            val file = repository.localMedia(media) ?: repository.fetchMedia(media, forceRedownload = false)
            if (file == null) {
                markAudioFailed()
                return@launch
            }
            audio.load(audioKey(groupId), file, startPositionMs = state.positionMs, seekAllowed = false, title = group.title.orEmpty())
        }
    }

    private fun playPause() {
        val state = listening ?: return
        if (_state.value.phase != AttemptPhase.Active) return
        val snapshot = audio.state.value
        if (snapshot.isPlaying) {
            audio.pause()
            persistListening(force = true)
            return
        }
        val next = state.play() ?: return
        listening = next
        if (snapshot.key != listeningGroupId?.let(::audioKey)) ensureAudioLoaded()
        audio.play()
        rebuild()
    }

    private fun retryAudio() {
        val groupId = listeningGroupId ?: return
        val group = pkg?.groups?.firstOrNull { it.id == groupId } ?: return
        val media = group.audio ?: return
        val state = listening ?: return
        audioBusy = true
        rebuild()
        viewModelScope.launch {
            val file = repository.fetchMedia(media, forceRedownload = true)
            audioBusy = false
            if (file != null) {
                listening = state.recovered()
                audioRetried = false
                audio.load(audioKey(groupId), file, startPositionMs = state.positionMs, seekAllowed = false, title = group.title.orEmpty())
            }
            rebuild()
        }
    }

    private fun onAudio(snapshot: AudioSnapshot) {
        val groupId = listeningGroupId ?: return
        if (snapshot.key != audioKey(groupId)) return
        var state = listening ?: return
        when {
            snapshot.error -> {
                onAudioError(groupId)
                return
            }
            snapshot.ended && state.playActive -> {
                state = state.ended()
                listening = state
                persistListening(force = true)
                telemetry.logEvent("exam_audio_play", mapOf("play_no" to state.playsUsed, "completed" to true, "restored" to false))
            }
            snapshot.isPlaying -> {
                val (next, counted) = state.advance(snapshot.positionMs)
                listening = next
                if (counted) {
                    persistListening(force = true)
                    _events.trySend(ExamAttemptEvent.ListeningCounted)
                } else {
                    persistListening(force = false)
                }
            }
        }
        // The time-up rule pauses playback; a late tick never restarts it.
        if (_state.value.phase != AttemptPhase.Active && snapshot.isPlaying) audio.pause()
        rebuild()
    }

    /** EXAM-10: re-download once; then "Audio yüklənmədi", plays not consumed, `audioFailed` synced. */
    private fun onAudioError(groupId: String) {
        val group = pkg?.groups?.firstOrNull { it.id == groupId } ?: return
        val media = group.audio ?: return
        val state = listening ?: return
        if (!audioRetried) {
            audioRetried = true
            audioBusy = true
            rebuild()
            viewModelScope.launch {
                val file = repository.fetchMedia(media, forceRedownload = true)
                audioBusy = false
                if (file == null) {
                    markAudioFailed()
                } else {
                    audio.load(audioKey(groupId), file, startPositionMs = state.positionMs, seekAllowed = false, title = group.title.orEmpty())
                }
                rebuild()
            }
            return
        }
        markAudioFailed()
    }

    private fun markAudioFailed() {
        val state = listening ?: return
        if (state.audioFailed) return
        listening = state.failed()
        telemetry.logEvent("exam_audio_failed", mapOf("variant_id" to (attempt?.variantId ?: ""), "error_type" to "playback"))
        persistListening(force = true)
        scheduleSync(immediate = true)
        rebuild()
    }

    /** Position saved every 1 s (OQ-D43); immediately on pause, end, count and failure. */
    private fun persistListening(force: Boolean) {
        val groupId = listeningGroupId ?: return
        val state = listening ?: return
        val now = clock.nowMs()
        if (!force && now - lastListeningSaveMs < LISTENING_SAVE_MS) return
        lastListeningSaveMs = now
        viewModelScope.launch {
            repository.saveListening(
                attemptId,
                LocalListening(
                    groupId = groupId,
                    playsUsed = state.playsUsed,
                    audioFailed = state.audioFailed,
                    positionMs = state.positionMs,
                    playInProgress = state.persistedPlayInProgress,
                ),
            )
            if (force) scheduleSync(immediate = true)
        }
    }

    private fun stopAudio() {
        listeningGroupId?.let { audio.stop(audioKey(it)) }
    }

    // ------------------------------------------------------------------ state

    private fun rebuild() {
        val loaded = pkg ?: return
        val current = attempt ?: return
        val answers = snapshot?.answers.orEmpty()
        val sections = loaded.sections.associateBy { it.id }
        val groups = loaded.groups.associateBy { it.id }
        val skippedGroupIds = if (current.skipListening) {
            loaded.groups.filter { it.kind == ExamGroupKind.LISTENING }.map { it.id }.toSet()
        } else {
            emptySet()
        }
        val pages = loaded.tasks
            .filterNot { it.groupId != null && it.groupId in skippedGroupIds }
            .map { task ->
                val stored = answers[task.id]
                val group = task.groupId?.let(groups::get)
                ExamTaskUi(
                    id = task.id,
                    number = task.position,
                    sectionId = task.sectionId,
                    sectionTitle = sections[task.sectionId]?.title.orEmpty(),
                    groupId = task.groupId,
                    groupKind = group?.kind,
                    type = task.type,
                    prompt = task.prompt,
                    instructions = group?.instructions,
                    options = task.options.map { ExamOptionUi(it.id, it.letter, it.text) },
                    selectedOptionId = stored?.optionId,
                    text = drafts[task.id] ?: stored?.text.orEmpty(),
                    flagged = stored?.flagged == true,
                )
            }
        val answered = pages.count { it.answered }
        val navigator = loaded.sections.map { section ->
            val all = loaded.tasks.filter { it.sectionId == section.id }
            val indices = pages.withIndex().filter { it.value.sectionId == section.id }.map { it.index }
            NavigatorSectionUi(
                title = section.title,
                firstNumber = all.minOfOrNull { it.position } ?: 0,
                lastNumber = all.maxOfOrNull { it.position } ?: 0,
                skipped = indices.isEmpty() && all.isNotEmpty(),
                pageIndices = indices,
            )
        }
        val passages = loaded.groups.filter { it.kind == ExamGroupKind.READING && it.passage != null }
            .associate { it.id to ExamPassageUi(it.id, it.title, it.passage.orEmpty()) }
        _state.update { state ->
            state.copy(
                totalTasks = loaded.tasks.size,
                sectionTitles = loaded.sections.map { it.title },
                pages = pages,
                currentIndex = state.currentIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0)),
                passages = passages,
                player = playerUi(),
                navigator = navigator,
                counts = ExamCounts(answered = answered, unanswered = pages.size - answered, flagged = pages.count { it.flagged }),
                maxTextLength = loaded.maxTextLength,
                language = loaded.language,
            )
        }
    }

    private fun playerUi(): ExamPlayerUi? {
        val groupId = listeningGroupId ?: return null
        val state = listening ?: return null
        val snapshot = audio.state.value.takeIf { it.key == audioKey(groupId) }
        val playing = snapshot?.isPlaying == true
        val caption = when {
            audioBusy -> PlayerCaption.Redownloading
            snapshot?.preparing == true && state.playActive -> PlayerCaption.Preparing
            state.restored && !playing -> PlayerCaption.Restored
            snapshot?.interrupted == true && !playing && state.playActive -> PlayerCaption.Interrupted
            state.showCountsAfterCaption && playing -> PlayerCaption.CountsAfter
            else -> PlayerCaption.None
        }
        return ExamPlayerUi(
            groupId = groupId,
            maxPlays = state.maxPlays,
            playsUsed = state.playsUsed,
            playsLeft = state.playsLeft,
            currentPlayNumber = state.currentPlayNumber,
            playActive = state.playActive,
            playing = playing,
            positionMs = if (state.playActive) state.positionMs else 0,
            durationMs = snapshot?.durationMs?.takeIf { it > 0 }
                ?: pkg?.groups?.firstOrNull { it.id == groupId }?.audio?.durationMs ?: 0,
            countsAfterSeconds = (state.countsAfterMs / MS_PER_SECOND).toInt(),
            caption = caption,
            exhausted = state.exhausted,
            failed = state.audioFailed,
            failedOffline = state.audioFailed && !online.value,
            busy = audioBusy,
            readOnly = _state.value.phase != AttemptPhase.Active,
        )
    }

    override fun onCleared() {
        // Leaving the attempt: the running play pauses (it resumes at the same position later).
        if (audio.state.value.isPlaying) audio.pause()
    }

    internal companion object {
        const val TICK_MS = 1_000L
        const val MINUTE_MS = 60_000L
        const val MS_PER_SECOND = 1_000L
        const val TEXT_DEBOUNCE_MS = 500L
        const val SYNC_DEBOUNCE_MS = 1_000L
        const val LISTENING_SAVE_MS = 1_000L

        /** A larger drop between two ticks means the app was away: no stale notice. */
        const val NOTICE_JUMP_MS = 3_000L
        val NOTICE_MINUTES = listOf(30, 10, 1)
    }
}
