package com.sabit.duelloquiz.feature.profile

import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.model.ApiErrorCode
import com.sabit.duelloquiz.core.model.CategoryProgress
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.Profile
import com.sabit.duelloquiz.core.model.duel.CalibrationHint
import com.sabit.duelloquiz.core.model.duel.CalibrationLevel
import com.sabit.duelloquiz.core.model.duel.DuelRecord
import com.sabit.duelloquiz.core.model.duel.DuelStats
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestDuelHistoryRepository
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestProfileRepository
import com.sabit.duelloquiz.core.testing.TestProgressRepository
import com.sabit.duelloquiz.core.testing.testProgressSummary
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val profile = TestProfileRepository(Profile("u1", "Aysel", null, 1016, createdAtEpochMs = 0))
    private val history = TestDuelHistoryRepository()
    private val network = TestNetworkMonitor()
    private val events = mutableListOf<ProfileEvent>()

    private val progress = TestProgressRepository()

    private fun stats(total: Int = 22) = DuelStats(
        DuelRecord(12, 9, 1, total),
        listOf(CalibrationLevel(1, 8, 7), CalibrationLevel(2, 6, 4), CalibrationLevel(3, 6, 2)),
    )

    private fun TestScope.viewModel(duelEnabled: Boolean = true): ProfileViewModel {
        profile.nextRefresh = DataResult.Success(Profile("u1", "Aysel", null, 1016, createdAtEpochMs = 0))
        val vm = ProfileViewModel(
            profile, history, network, AppConfig("1", 1, "", duelEnabled = duelEnabled), progress,
            WallClock { testScheduler.currentTime },
        )
        backgroundScope.launch { vm.events.collect { events += it } }
        runCurrent()
        return vm
    }

    private fun TestScope.editing(vm: ProfileViewModel, value: String) {
        vm.onAction(ProfileAction.EditNameClicked)
        runCurrent()
        vm.onAction(ProfileAction.NameChanged(value))
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
    }

    // Progress (PROF-09 … PROF-11)

    @Test
    fun levelStreakAndCategoryLevelsComeFromTheServer() = runTest {
        progress.nextRefresh = DataResult.Success(
            testProgressSummary(streak = 7, todayActive = true).copy(
                freezeAvailable = true,
                categories = listOf(
                    CategoryProgress(1, "cografiya", "Coğrafiya", "geography", 240, 2, 140, 200),
                    CategoryProgress(2, "tarix", "Tarix", "history", 0, 1, 0, 100),
                ),
            ),
        )
        val ui = (viewModel(duelEnabled = false).uiState.value.progress as SectionUi.Content).value

        assertEquals(5, ui.level)
        assertEquals(270L, ui.xpIntoLevel)
        assertEquals(1_270L, ui.xpTotal)
        assertEquals(7, ui.streakCurrent)
        assertEquals(12, ui.streakBest)
        assertEquals(true, ui.todayActive)
        assertTrue(ui.freezeAvailable)
        assertEquals(listOf("Coğrafiya", "Tarix"), ui.categories.map { it.name })
        assertEquals(listOf(2, 1), ui.categories.map { it.level })
        assertEquals(60L, ui.categories[0].xpToNext)
        // Same accent slots as the home tiles (server order).
        assertEquals(listOf("violet", "blue"), ui.categories.map { it.accent })
    }

    @Test
    fun anOldCacheHidesTheTodayLineAndAFailureWithoutCacheIsAnError() = runTest {
        progress.nextRefresh = DataResult.Success(testProgressSummary(todayActive = true, nextResetEpochMs = -1))
        val stale = (viewModel().uiState.value.progress as SectionUi.Content).value
        assertNull(stale.todayActive)

        val failing = TestProgressRepository()
        failing.nextRefresh = DataResult.Failure(DataError.Server)
        val vm = ProfileViewModel(
            profile, history, network, AppConfig("1", 1, ""), failing, WallClock { testScheduler.currentTime },
        )
        runCurrent()
        assertEquals(SectionUi.Error, vm.uiState.value.progress)
    }

    // Sections (PROF-01 … PROF-08)

    @Test
    fun everythingIsReloadedOnOpenAndShown() = runTest {
        history.nextStats = DataResult.Success(stats())
        history.nextMatches = DataResult.Success(emptyList())
        val state = viewModel().uiState.value
        val header = (state.header as SectionUi.Content).value
        assertEquals("Aysel", header.displayName)
        assertEquals(1016, header.rating)
        assertEquals(1, profile.refreshCount)
        assertEquals(1, history.statsRefreshCount)
        val stats = (state.stats as SectionUi.Content).value
        assertEquals(22, stats.stats.record.total)
        // 1: 7/8, 2: 4/6, 3: 2/6 → overconfident at level 3.
        assertEquals(CalibrationHint.OVERCONFIDENT, stats.hint)
        assertFalse(state.empty)
    }

    @Test
    fun noCountedMatchShowsTheEmptyCard() = runTest {
        history.nextStats = DataResult.Success(stats(total = 0))
        assertTrue(viewModel().uiState.value.empty)
    }

    @Test
    fun withTheDuelFlagOffOnlyTheHeaderIsLoaded() = runTest {
        val vm = viewModel(duelEnabled = false)
        assertFalse(vm.uiState.value.duelEnabled)
        assertEquals(0, history.statsRefreshCount)
        assertFalse(vm.uiState.value.empty)
    }

    @Test
    fun aFailingSectionDoesNotBlockTheOthers() = runTest {
        history.nextStats = DataResult.Failure(DataError.Server)
        history.nextMatches = DataResult.Success(emptyList())
        val state = viewModel().uiState.value
        assertEquals(SectionUi.Error, state.stats)
        assertTrue(state.matches is SectionUi.Content)
        assertTrue(state.header is SectionUi.Content)
    }

    @Test
    fun offlineWithCacheShowsTheBanner() = runTest {
        network.online.value = false
        val state = viewModel().uiState.value
        assertTrue(state.showOfflineBanner)
        assertFalse(state.fullScreenOffline)
    }

    // Edit name (PROF-06, OQ-042)

    @Test
    fun anUnchangedNameClosesWithoutARequest() = runTest {
        val vm = viewModel()
        editing(vm, "  Aysel ")
        assertNull(vm.uiState.value.editName)
        assertTrue(profile.updatedNames.isEmpty())
    }

    @Test
    fun clientRulesRejectBeforeAnyRequest() = runTest {
        val vm = viewModel()
        editing(vm, "   ")
        assertEquals(NameError.REQUIRED, vm.uiState.value.editName?.error)
        vm.onAction(ProfileAction.NameChanged("A"))
        runCurrent()
        assertNull(vm.uiState.value.editName?.error)
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
        assertEquals(NameError.LENGTH, vm.uiState.value.editName?.error)
        vm.onAction(ProfileAction.NameChanged("Aysel 😀"))
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
        assertEquals(NameError.CHARACTERS, vm.uiState.value.editName?.error)
        vm.onAction(ProfileAction.NameChanged("a".repeat(21)))
        runCurrent()
        assertTrue(vm.uiState.value.editName!!.overLimit)
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
        assertEquals(NameError.LENGTH, vm.uiState.value.editName?.error)
        assertTrue(profile.updatedNames.isEmpty())
    }

    @Test
    fun theCounterCountsCodePointsAfterNormalization() = runTest {
        val vm = viewModel()
        vm.onAction(ProfileAction.EditNameClicked)
        runCurrent()
        vm.onAction(ProfileAction.NameChanged("  Əli   Məmmədov "))
        runCurrent()
        assertEquals(12, vm.uiState.value.editName?.length)
    }

    @Test
    fun aValidNameIsSavedNormalized() = runTest {
        val vm = viewModel()
        editing(vm, "  Əli   Məmmədov ")
        assertEquals(listOf("Əli Məmmədov"), profile.updatedNames)
        assertNull(vm.uiState.value.editName)
        assertEquals(ProfileEvent.ShowMessage(ProfileMessage.NAME_SAVED), events.last())
        assertEquals("Əli Məmmədov", (vm.uiState.value.header as SectionUi.Content).value.displayName)
    }

    @Test
    fun serverErrorsAreShownOnTheField() = runTest {
        val vm = viewModel()
        profile.nextUpdate = DataResult.Failure(DataError.Api(ApiErrorCode.DisplayNameNotAllowed))
        editing(vm, "Admin")
        assertEquals(NameError.NOT_ALLOWED, vm.uiState.value.editName?.error)
        assertEquals("Admin", vm.uiState.value.editName?.value)

        profile.nextUpdate = DataResult.Failure(DataError.Api(ApiErrorCode.InvalidDisplayName))
        vm.onAction(ProfileAction.NameChanged("Murad"))
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
        assertEquals(NameError.INVALID, vm.uiState.value.editName?.error)

        profile.nextUpdate = DataResult.Failure(DataError.Timeout)
        vm.onAction(ProfileAction.SaveName)
        runCurrent()
        assertEquals(NameError.FAILED, vm.uiState.value.editName?.error)
        assertFalse(vm.uiState.value.editName!!.saving)
    }

    @Test
    fun offlineSaveShowsTheOfflineErrorAndKeepsTheValue() = runTest {
        val vm = viewModel()
        network.online.value = false
        runCurrent()
        editing(vm, "Murad")
        assertEquals(NameError.OFFLINE, vm.uiState.value.editName?.error)
        assertEquals("Murad", vm.uiState.value.editName?.value)
        assertTrue(profile.updatedNames.isEmpty())
    }

    @Test
    fun aRunningSaveIsNeverSentTwice() = runTest {
        val vm = viewModel()
        profile.updateGate = CompletableDeferred()
        editing(vm, "Murad")
        assertTrue(vm.uiState.value.editName!!.saving)
        vm.onAction(ProfileAction.SaveName)
        vm.onAction(ProfileAction.DismissEdit)
        runCurrent()
        assertEquals(1, profile.updatedNames.size)
        assertTrue(vm.uiState.value.editName != null)
        profile.updateGate!!.complete(Unit)
        runCurrent()
        assertNull(vm.uiState.value.editName)
    }

    @Test
    fun startDuelOfflineShowsTheMessage() = runTest {
        val vm = viewModel()
        network.online.value = false
        runCurrent()
        vm.onAction(ProfileAction.StartDuelClicked)
        runCurrent()
        assertEquals(ProfileEvent.ShowMessage(ProfileMessage.DUEL_NEEDS_INTERNET), events.last())
        network.online.value = true
        runCurrent()
        vm.onAction(ProfileAction.StartDuelClicked)
        runCurrent()
        assertEquals(ProfileEvent.OpenDuel, events.last())
    }

    // Dates (PROF-02, PROF-04)

    @Test
    fun historyDatesUseTheDeviceTimeZone() {
        val utc = TimeZone.getTimeZone("UTC")
        val now = 1_792_144_800_000L // 2026-10-16 10:00 UTC
        assertEquals(HistoryDate.Today, ProfileDates.historyDate(now - 3_600_000, now, utc))
        assertEquals(HistoryDate.Yesterday, ProfileDates.historyDate(now - DAY, now, utc))
        assertEquals(HistoryDate.ThisYear(13, 9), ProfileDates.historyDate(now - 3 * DAY, now, utc))
        assertEquals(HistoryDate.OtherYear(16, 9, 2025), ProfileDates.historyDate(now - 365 * DAY, now, utc))
        assertEquals(MonthYear(9, 2026), ProfileDates.monthYear(now, utc))
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
