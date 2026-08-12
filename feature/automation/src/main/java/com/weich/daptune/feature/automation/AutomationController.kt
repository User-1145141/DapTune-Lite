package com.weich.daptune.feature.automation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val recoveryScheduler: AutomationRecoveryScheduler,
) {
    fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, EqAutomationService::class.java).setAction(EqAutomationService.ActionStart),
        )
        recoveryScheduler.onAutomationStarted()
    }

    fun stop() {
        recoveryScheduler.onAutomationStopped()
        context.stopService(Intent(context, EqAutomationService::class.java))
    }
}
