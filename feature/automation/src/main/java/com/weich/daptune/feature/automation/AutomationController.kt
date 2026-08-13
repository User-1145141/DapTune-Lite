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
) {
    fun start() {
        ContextCompat.startForegroundService(
            context,
            Intent(context, EqAutomationService::class.java).setAction(EqAutomationService.ActionStart),
        )
    }

    fun stop() {
        context.stopService(Intent(context, EqAutomationService::class.java))
    }
}
