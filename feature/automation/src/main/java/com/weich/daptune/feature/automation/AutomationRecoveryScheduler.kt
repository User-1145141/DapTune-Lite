package com.weich.daptune.feature.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms one system-owned recovery before the app UI leaves the foreground.
 *
 * HyperOS can remove the task and kill its process in the same transaction, so
 * post-removal callbacks are not a reliable place to register recovery. This
 * scheduler uses no polling, worker, secondary process, or exact-alarm access.
 */
@Singleton
class AutomationRecoveryScheduler @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val recoveryOperation = PendingIntent.getForegroundService(
        appContext,
        RecoveryRequestCode,
        Intent(appContext, EqAutomationService::class.java)
            .setAction(EqAutomationService.ActionRecover),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    @Volatile
    private var automationRunning = false

    fun onAutomationStarted() {
        automationRunning = true
    }

    fun onAutomationStopped() {
        automationRunning = false
        disarm()
    }

    fun arm() {
        if (!automationRunning) return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + RecoveryDelayMillis,
            recoveryOperation,
        )
    }

    fun disarm() {
        alarmManager.cancel(recoveryOperation)
    }

    private companion object {
        const val RecoveryRequestCode = 0xDA21
        const val RecoveryDelayMillis = 5_000L
    }
}
