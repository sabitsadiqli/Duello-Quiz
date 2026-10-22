package com.sabit.duelloquiz.feature.settings.notifications

import app.cash.turbine.test
import com.sabit.duelloquiz.core.model.DataError
import com.sabit.duelloquiz.core.model.DataResult
import com.sabit.duelloquiz.core.model.NotificationSettings
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.SystemNotificationState
import com.sabit.duelloquiz.core.testing.MainDispatcherRule
import com.sabit.duelloquiz.core.testing.TestNetworkMonitor
import com.sabit.duelloquiz.core.testing.TestNotificationPromptRepository
import com.sabit.duelloquiz.core.testing.TestNotificationSettingsRepository
import com.sabit.duelloquiz.core.testing.TestSystemNotifications
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val both = NotificationSettings(dailyReminder = true, streakReminder = true)
    private val repository = TestNotificationSettingsRepository().apply { nextRefresh = DataResult.Success(both) }
    private val system = TestSystemNotifications()
    private val prompts = TestNotificationPromptRepository()
    private val network = TestNetworkMonitor(online = true)

    private fun TestScope.viewModel(): NotificationSettingsViewModel {
        val vm = NotificationSettingsViewModel(repository, system, prompts, network)
        backgroundScope.launch { vm.uiState.collect {} }
        runCurrent()
        return vm
    }

    @Test
    fun loadedValuesAreShownWhenThePhoneAllowsThem() = runTest {
        val ui = viewModel().uiState.value

        assertFalse(ui.loading)
        assertFalse(ui.blocked)
        assertEquals(ReminderSwitchUi(checked = true, enabled = true, channelOff = false), ui.daily)
        assertEquals(ReminderSwitchUi(checked = true, enabled = true, channelOff = false), ui.streak)
    }

    @Test
    fun effectiveValueIsServerAndPermissionAndChannel() = runTest {
        system.set(SystemNotificationState(permissionGranted = true, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = false))
        val vm = viewModel()

        val ui = vm.uiState.value
        assertTrue(ui.daily.checked)
        assertEquals(ReminderSwitchUi(checked = false, enabled = false, channelOff = true), ui.streak)

        system.set(SystemNotificationState(permissionGranted = true, appNotificationsEnabled = false, dailyChannelEnabled = true, streakChannelEnabled = true))
        runCurrent()
        val blocked = vm.uiState.value
        assertTrue(blocked.blocked)
        assertFalse(blocked.daily.checked || blocked.daily.enabled)
        assertFalse(blocked.streak.checked || blocked.streak.enabled)
        // The server value itself never changes because of the phone.
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun toggleIsOptimisticAndRevertsWhenSavingFails() = runTest {
        val vm = viewModel()
        repository.updateGate = CompletableDeferred()
        repository.nextUpdate = DataResult.Failure(DataError.Server)

        vm.events.test {
            vm.onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = false))
            runCurrent()
            assertFalse(vm.uiState.value.daily.checked)

            repository.updateGate?.complete(Unit)
            runCurrent()
            assertEquals(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.SaveFailed), awaitItem())
        }
        assertTrue(vm.uiState.value.daily.checked)
        assertEquals(NotificationSettings(dailyReminder = false, streakReminder = true), repository.updates.single())
    }

    @Test
    fun toggleSavesBothSwitchesTogether() = runTest {
        val vm = viewModel()

        vm.onAction(NotificationSettingsAction.Toggle(ReminderType.STREAK, enabled = false))
        runCurrent()

        assertFalse(vm.uiState.value.streak.checked)
        assertTrue(vm.uiState.value.daily.checked)
        assertEquals(NotificationSettings(dailyReminder = true, streakReminder = false), repository.updates.single())
    }

    @Test
    fun offlineToggleRevertsAtOnce() = runTest {
        val vm = viewModel()
        network.online.value = false
        runCurrent()

        vm.events.test {
            vm.onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = false))
            assertEquals(NotificationSettingsEvent.ShowMessage(NotificationSettingsMessage.Offline), awaitItem())
        }
        assertTrue(vm.uiState.value.daily.checked)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun turningOnWithoutPermissionAsksFirstAndSavesOnlyAfterAllow() = runTest {
        system.set(SystemNotificationState(permissionGranted = false, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = true))
        repository.nextRefresh = DataResult.Success(NotificationSettings(dailyReminder = false, streakReminder = true))
        val vm = viewModel()
        assertTrue(vm.uiState.value.blocked)
        assertTrue(vm.uiState.value.daily.enabled)

        vm.events.test {
            vm.onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = true))
            runCurrent()
            assertEquals(NotificationSettingsEvent.RequestPermission, awaitItem())
            assertTrue(repository.updates.isEmpty())

            system.set(SystemNotificationState.AllAllowed)
            vm.onAction(NotificationSettingsAction.PermissionResult(granted = true, blocked = false))
            runCurrent()
        }
        assertEquals(NotificationSettings(dailyReminder = true, streakReminder = true), repository.updates.single())
        assertTrue(vm.uiState.value.daily.checked)
    }

    @Test
    fun deniedKeepsTheSwitchOffAndRemembersDontAskAgain() = runTest {
        system.set(SystemNotificationState(permissionGranted = false, appNotificationsEnabled = true, dailyChannelEnabled = true, streakChannelEnabled = true))
        val vm = viewModel()

        vm.events.test {
            vm.onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = true))
            runCurrent()
            assertEquals(NotificationSettingsEvent.RequestPermission, awaitItem())
            vm.onAction(NotificationSettingsAction.PermissionResult(granted = false, blocked = true))
            runCurrent()
            expectNoEvents()

            // Next time the system will not ask: open the app's notification settings instead.
            vm.onAction(NotificationSettingsAction.Toggle(ReminderType.DAILY, enabled = true))
            runCurrent()
            assertTrue(awaitItem() is NotificationSettingsEvent.OpenSettings)
        }
        assertTrue(prompts.isBlocked)
        assertTrue(repository.updates.isEmpty())
    }

    @Test
    fun loadFailureWithoutCacheShowsTheErrorRowAndRetries() = runTest {
        repository.nextRefresh = DataResult.Failure(DataError.Offline)
        val vm = viewModel()
        assertTrue(vm.uiState.value.loadError)

        repository.nextRefresh = DataResult.Success(both)
        vm.onAction(NotificationSettingsAction.Retry)
        runCurrent()
        assertFalse(vm.uiState.value.loadError)
        assertTrue(vm.uiState.value.daily.checked)
    }
}
