package com.sabit.duelloquiz

import android.app.UiModeManager
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.sabit.duelloquiz.core.designsystem.theme.DuelloTheme
import com.sabit.duelloquiz.core.designsystem.theme.isDarkTheme
import com.sabit.duelloquiz.core.model.ReminderType
import com.sabit.duelloquiz.core.model.ThemeMode
import com.sabit.duelloquiz.core.notifications.NotificationIntents
import com.sabit.duelloquiz.core.notifications.ReminderOpens
import com.sabit.duelloquiz.core.notifications.SystemNotifications
import com.sabit.duelloquiz.navigation.DuelloNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    @Inject
    lateinit var reminderOpens: ReminderOpens

    @Inject
    lateinit var systemNotifications: SystemNotifications

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // NOTIF-05: opened by tapping a reminder (only on a fresh start, not after recreation).
        if (savedInstanceState == null) handleReminderIntent(intent)

        // Never a blank frame or the wrong first screen: keep the splash until theme and
        // sign-in state are known, with no upper time limit (AUTH-01).
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is MainActivityUiState.Loading }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .filterIsInstance<MainActivityUiState.Ready>()
                    .map { it.themeMode }
                    .distinctUntilChanged()
                    .collect { applySystemNightMode(it) }
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val ready = uiState as? MainActivityUiState.Ready ?: return@setContent
            val darkTheme = ready.themeMode.isDarkTheme()

            // System bar icons follow the app theme, not only the system setting.
            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                )
                onDispose {}
            }

            DuelloTheme(darkTheme = darkTheme) {
                DuelloNavHost(authState = ready.authState, duelEnabled = BuildConfig.DUEL_ENABLED)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleReminderIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the permission or channels in the system settings (NOTIF-02).
        systemNotifications.refresh()
    }

    /** Only the reminder type is read; home shows the current server state (NOTIF-05). */
    private fun handleReminderIntent(intent: Intent?) {
        val type = ReminderType.fromWire(intent?.getStringExtra(NotificationIntents.EXTRA_TYPE)) ?: return
        intent?.removeExtra(NotificationIntents.EXTRA_TYPE)
        reminderOpens.onOpened(type)
    }

    /**
     * Android 12+ persists the app's night mode for the system splash screen, so a manual
     * Light/Dark choice is used from the very first frame of the next launch (SET-01).
     * The activity handles the resulting uiMode change itself (configChanges="uiMode").
     */
    private fun applySystemNightMode(themeMode: ThemeMode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val mode = when (themeMode) {
            ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
            ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
            ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
        }
        getSystemService<UiModeManager>()?.setApplicationNightMode(mode)
    }
}
