package com.sabit.duelloquiz.core.notifications.exam

import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.sabit.duelloquiz.core.auth.AuthRepository
import com.sabit.duelloquiz.core.auth.AuthState
import com.sabit.duelloquiz.core.common.AppConfig
import com.sabit.duelloquiz.core.common.Telemetry
import com.sabit.duelloquiz.core.common.WallClock
import com.sabit.duelloquiz.core.common.di.ApplicationScope
import com.sabit.duelloquiz.core.data.exam.ExamRepository
import com.sabit.duelloquiz.core.data.exam.LocalAttemptPhase
import com.sabit.duelloquiz.core.data.exam.LocalExamAttempt
import com.sabit.duelloquiz.core.notifications.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.sabit.duelloquiz.core.designsystem.R as DesignR

/** Rules of the "10 dəqiqə qalıb" reminder (EXAM-27, OQ-D45) as pure functions (unit-tested). */
object ExamReminderRules {
    const val LEAD_MS = 10 * 60_000L
    const val MIN_REMAINING_MS = 5 * 60_000L

    /** Local wall-clock time to fire the alarm: `endsAt − 10 min` converted with the clock offset. */
    fun triggerAtLocalMs(attempt: LocalExamAttempt): Long = attempt.endsAtEpochMs - attempt.clockOffsetMs - LEAD_MS

    enum class Decision { POST, SKIP_FOREGROUND, SKIP_LATE, SKIP_ENDED, SKIP_ALREADY_POSTED, BLOCKED }

    fun decide(attempt: LocalExamAttempt?, localNowMs: Long, appInForeground: Boolean, canPost: Boolean): Decision = when {
        attempt == null || attempt.phase != LocalAttemptPhase.RUNNING || attempt.isTimeUp(localNowMs) -> Decision.SKIP_ENDED
        attempt.reminderPosted -> Decision.SKIP_ALREADY_POSTED
        appInForeground -> Decision.SKIP_FOREGROUND
        attempt.remainingMs(localNowMs) < MIN_REMAINING_MS -> Decision.SKIP_LATE
        !canPost -> Decision.BLOCKED
        else -> Decision.POST
    }

    /** Whole minutes left when posting ("10 dəqiqə qalıb", never below 5). */
    fun minutesLeft(attempt: LocalExamAttempt, localNowMs: Long): Int = (attempt.remainingMs(localNowMs) / 60_000L).toInt()
}

/** Whether any activity of the app is started (EXAM-27: never post while the app is in the foreground). */
@Singleton
class AppVisibility @Inject constructor() {
    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()
    private var started = 0

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    started++
                    _foreground.value = true
                }

                override fun onActivityStopped(activity: Activity) {
                    started = (started - 1).coerceAtLeast(0)
                    _foreground.value = started > 0
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}

/** The running attempt the app was opened for from the reminder (EXAM-27 tap → the attempt). */
@Singleton
class ExamOpens @Inject constructor() {
    private val _pending = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = _pending.asStateFlow()

    fun onOpened(attemptId: String) {
        _pending.value = attemptId
    }

    fun consume(): String? = _pending.value.also { _pending.value = null }

    companion object {
        const val EXTRA_EXAM_ATTEMPT_ID = "exam_attempt_id"
    }
}

/**
 * EXAM-27 (OQ-D45): one local "10 dəqiqə qalıb" notification per attempt, only while the app is
 * in the background, with ≥ 5 min left, never asking for the permission. The inexact alarm
 * (`setAndAllowWhileIdle`, no exact-alarm permission) is set when the app goes to the background
 * with a running attempt and cancelled — together with a shown notification — when the app comes
 * to the foreground, the attempt ends or the user signs out (EXAM-23). Reboot and app update
 * reschedule it ([ExamReminderBootReceiver]). Started by the Application behind the exams flag.
 */
@Singleton
class ExamReminderController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: ExamRepository,
    private val visibility: AppVisibility,
    private val authRepository: AuthRepository,
    private val appConfig: AppConfig,
    private val clock: WallClock,
    private val telemetry: Telemetry,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    fun start(application: Application) {
        if (!appConfig.examsEnabled) return
        visibility.register(application)
        scope.launch {
            combine(visibility.foreground, repository.activeAttempt) { foreground, attempt -> foreground to attempt }
                .distinctUntilChanged()
                .collect { (foreground, attempt) -> apply(foreground, attempt) }
        }
        // EXAM-23: local attempts, marks and caches of a signed-out account go (packages stay).
        scope.launch {
            authRepository.authState
                .map { it is AuthState.SignedOut }
                .distinctUntilChanged()
                .collect { signedOut ->
                    if (signedOut) {
                        cancelAll()
                        repository.clearAccountData()
                    }
                }
        }
    }

    /** Reboot / app update: re-arm the alarm for a running attempt (the app is in the background). */
    suspend fun reschedule() {
        if (!appConfig.examsEnabled) return
        apply(visibility.foreground.value, repository.activeAttempt.first())
    }

    /** Info screen hint (EXAM-02): permission granted, app notifications and the exam channel on. */
    fun canPost(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = context.getSystemService<NotificationManager>()?.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }
        return true
    }

    private fun apply(foreground: Boolean, attempt: LocalExamAttempt?) {
        when {
            attempt == null || attempt.phase != LocalAttemptPhase.RUNNING -> cancelAll()
            foreground -> cancelAll()
            !attempt.reminderPosted -> schedule(attempt)
        }
    }

    private fun schedule(attempt: LocalExamAttempt) {
        val alarms = context.getSystemService<AlarmManager>() ?: return
        val now = clock.nowMs()
        if (attempt.remainingMs(now) < ExamReminderRules.MIN_REMAINING_MS) return
        val at = ExamReminderRules.triggerAtLocalMs(attempt).coerceAtLeast(now)
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(attempt.attemptId))
    }

    fun cancelAll() {
        context.getSystemService<AlarmManager>()?.cancel(alarmIntent(null))
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, NOTIFICATION_ID)
    }

    /** The alarm fired ([ExamReminderReceiver]). */
    suspend fun onAlarm(attemptId: String) {
        val attempt = repository.localAttempt(attemptId)
        val now = clock.nowMs()
        val canPost = canPost()
        val decision = ExamReminderRules.decide(attempt, now, visibility.foreground.value, canPost)
        telemetry.logEvent("exam_time_left_notification", mapOf("action" to decision.analytics()))
        if (decision != ExamReminderRules.Decision.POST || attempt == null) return
        post(attempt, ExamReminderRules.minutesLeft(attempt, now), attempt.remainingMs(now))
        repository.markReminderPosted(attemptId)
    }

    private fun post(attempt: LocalExamAttempt, minutes: Int, remainingMs: Long) {
        createChannel(context)
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ExamOpens.EXTRA_EXAM_ATTEMPT_ID, attempt.attemptId)
        } ?: return
        val pending = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = context.resources.getQuantityString(DesignR.plurals.notif_exam_time_left_title, minutes, minutes)
        val variant = context.getString(DesignR.string.exam_variant_title, attempt.variantNumber)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_duello)
            .setColor(ContextCompat.getColor(context, DesignR.color.notification_accent))
            .setContentTitle(title)
            .setContentText(context.getString(DesignR.string.notif_exam_time_left_body, variant))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setTimeoutAfter(remainingMs)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        @Suppress("MissingPermission") // canPost() checked before
        NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, NOTIFICATION_ID, notification)
    }

    private fun alarmIntent(attemptId: String?): PendingIntent {
        val intent = Intent(context, ExamReminderReceiver::class.java).apply {
            action = ACTION_ALARM
            if (attemptId != null) putExtra(ExamOpens.EXTRA_EXAM_ATTEMPT_ID, attemptId)
        }
        return PendingIntent.getBroadcast(context, REQUEST_ALARM, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun ExamReminderRules.Decision.analytics(): String = when (this) {
        ExamReminderRules.Decision.POST -> "posted"
        ExamReminderRules.Decision.SKIP_FOREGROUND -> "skipped_foreground"
        ExamReminderRules.Decision.SKIP_LATE, ExamReminderRules.Decision.SKIP_ENDED, ExamReminderRules.Decision.SKIP_ALREADY_POSTED ->
            "skipped_late"
        ExamReminderRules.Decision.BLOCKED -> "blocked"
    }

    companion object {
        /** The "Sınaqlar" channel (exam-attempt.md I), created on first use. */
        const val CHANNEL_ID = "exam"
        const val NOTIFICATION_TAG = "exam_time_left"
        private const val NOTIFICATION_ID = 0
        private const val REQUEST_ALARM = 2_701
        private const val REQUEST_OPEN = 2_702
        const val ACTION_ALARM = "com.sabit.duelloquiz.action.EXAM_TIME_LEFT"

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService<NotificationManager>() ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(DesignR.string.notif_channel_exam_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(DesignR.string.notif_channel_exam_description) }
            manager.createNotificationChannel(channel)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExamReminderEntryPoint {
    fun examReminderController(): ExamReminderController
}

/** The inexact EXAM-27 alarm. */
class ExamReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val attemptId = intent.getStringExtra(ExamOpens.EXTRA_EXAM_ATTEMPT_ID) ?: return
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, ExamReminderEntryPoint::class.java)
            .examReminderController()
        val pending = goAsync()
        CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                controller.onAlarm(attemptId)
            } finally {
                pending.finish()
            }
        }
    }
}

/** EXAM-27: reboot or app update during an attempt re-arms the alarm. */
class ExamReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val controller = EntryPointAccessors.fromApplication(context.applicationContext, ExamReminderEntryPoint::class.java)
            .examReminderController()
        val pending = goAsync()
        CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try {
                controller.reschedule()
            } finally {
                pending.finish()
            }
        }
    }
}
