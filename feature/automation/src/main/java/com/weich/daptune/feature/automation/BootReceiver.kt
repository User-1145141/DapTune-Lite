package com.weich.daptune.feature.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.weich.daptune.core.model.OperationLogAction
import com.weich.daptune.core.model.OperationLogEntry
import com.weich.daptune.core.model.OperationLogOutcome
import com.weich.daptune.domain.OperationLogRepository
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
    @Inject lateinit var operationLogs: OperationLogRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in AcceptedActions) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settings.first()
                val shouldStart = settings.automationEnabled &&
                    (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED || settings.applyAtBoot)
                if (shouldStart) {
                    runCatching { automationController.start() }
                        .onFailure { error ->
                            runCatching {
                                operationLogs.append(
                                    OperationLogEntry(
                                        occurredAtEpochMillis = System.currentTimeMillis(),
                                        action = OperationLogAction.AUTOMATION_START_FAILED,
                                        outcome = OperationLogOutcome.FAILURE,
                                        detail = error.message ?: error.javaClass.simpleName,
                                    ),
                                )
                            }
                        }
                }
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
