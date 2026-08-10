package com.weich.daptune.feature.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weich.daptune.domain.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var automationController: AutomationController

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in AcceptedActions) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                if (settings.automationEnabled && settings.applyAtBoot) automationController.start()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val AcceptedActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}
