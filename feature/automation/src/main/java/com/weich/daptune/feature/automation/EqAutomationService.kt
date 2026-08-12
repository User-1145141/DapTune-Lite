package com.weich.daptune.feature.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.OperationLogAction
import com.weich.daptune.core.model.OperationLogEntry
import com.weich.daptune.core.model.OperationLogOutcome
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.ProfileApplySource
import com.weich.daptune.domain.ApplyProfileUseCase
import com.weich.daptune.domain.AudioRouteMonitor
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.OperationLogRepository
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.ResolveProfileForRouteUseCase
import com.weich.daptune.domain.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

@AndroidEntryPoint
class EqAutomationService : Service() {
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var routeMonitor: AudioRouteMonitor
    @Inject lateinit var resolveProfile: ResolveProfileForRouteUseCase
    @Inject lateinit var applyProfile: ApplyProfileUseCase
    @Inject lateinit var operationLogs: OperationLogRepository
    @Inject lateinit var recoveryScheduler: AutomationRecoveryScheduler

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val routeProcessingMutex = Mutex()
    private var monitorJob: Job? = null
    @Volatile private var monitorReady = false
    @Volatile private var monitorGeneration = 0L
    private var lastAppliedRouteKey: String? = null
    private var lastAppliedCurveHash: Int? = null
    @Volatile private var lastNotificationText: String = "等待播放设备"
    private val dolbyStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            requestCurrentRoute(
                force = true,
                source = ProfileApplySource.DOLBY_RESTORED,
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            dolbyStateReceiver,
            IntentFilter(XiaomiDolbyStateAction),
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            recoveryScheduler.onAutomationStopped()
            serviceScope.launch {
                settingsRepository.setAutomationEnabled(false)
                mainHandler.post(::shutdown)
            }
            return START_NOT_STICKY
        }

        val action = intent?.action
        if (action != ActionStart && action != ActionRecover) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        recoveryScheduler.onAutomationStarted()
        promoteToForeground(lastNotificationText)
        when (action) {
            ActionRecover -> if (monitorJob?.isActive != true || !monitorReady) {
                restartMonitoring(ProfileApplySource.AUTOMATION_RECOVERY)
            }
            ActionStart -> if (monitorJob?.isActive != true || !monitorReady) {
                restartMonitoring(ProfileApplySource.AUTOMATION_START)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(dolbyStateReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun restartMonitoring(initialSource: ProfileApplySource) {
        monitorJob?.cancel()
        monitorReady = false
        monitorGeneration += 1L
        lastAppliedRouteKey = null
        lastAppliedCurveHash = null
        val generation = monitorGeneration
        monitorJob = serviceScope.launch {
            try {
                val initialized = withTimeout(MonitorStartupTimeoutMillis) {
                    profileRepository.ensureBuiltIns()
                    if (!settingsRepository.settings.first().automationEnabled) {
                        mainHandler.post(::shutdown)
                        return@withTimeout false
                    }

                    // Resolve synchronously once so a stale StateFlow placeholder cannot
                    // apply the speaker profile while a headset is already active.
                    processCurrentRouteSafely(
                        force = true,
                        source = initialSource,
                    )
                }
                if (!initialized || generation != monitorGeneration) return@launch
                monitorReady = true
                routeMonitor.routes.drop(1).collect { route ->
                    processRouteSafely(
                        route = route,
                        force = false,
                        source = ProfileApplySource.ROUTE_CHANGE,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                recordMonitorFailure(error)
                notify("自动切换恢复中")
            } finally {
                if (generation == monitorGeneration) monitorReady = false
            }
        }
    }

    private fun requestCurrentRoute(force: Boolean, source: ProfileApplySource) {
        if (monitorJob?.isActive != true || !monitorReady) {
            mainHandler.post { restartMonitoring(ProfileApplySource.AUTOMATION_RECOVERY) }
            return
        }
        serviceScope.launch {
            processCurrentRouteSafely(force = force, source = source)
        }
    }

    private suspend fun processCurrentRouteSafely(
        force: Boolean,
        source: ProfileApplySource,
    ): Boolean = processRouteSafely(
        route = routeMonitor.currentRoute(),
        force = force,
        source = source,
    )

    private suspend fun processRouteSafely(
        route: OutputRoute,
        force: Boolean,
        source: ProfileApplySource,
    ): Boolean = try {
        withTimeout(RouteOperationTimeoutMillis) {
            processRoute(route = route, force = force, source = source)
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        recordMonitorFailure(error)
        notify("自动切换恢复中")
        false
    }

    private suspend fun processRoute(
        route: OutputRoute,
        force: Boolean,
        source: ProfileApplySource,
    ) = routeProcessingMutex.withLock {
        if (!settingsRepository.settings.first().automationEnabled) {
            mainHandler.post(::shutdown)
            return@withLock
        }
        deviceRepository.rememberRoute(route)
        val profile = resolveProfile(route) ?: return@withLock
        val curveHash = profile.curve.stableHash()
        if (!force && route.key == lastAppliedRouteKey && curveHash == lastAppliedCurveHash) {
            return@withLock
        }

        notify("正在应用 ${profile.name} · ${route.displayName}")
        when (val result = applyProfile(profile.id, route, source)) {
            is DapApplyResult.Success -> {
                lastAppliedRouteKey = route.key
                lastAppliedCurveHash = curveHash
                notify("${profile.name} · ${route.displayName}")
            }
            is DapApplyResult.Failure -> notify(failureLabel(result, profile, route))
        }
    }

    private fun failureLabel(
        failure: DapApplyResult.Failure,
        profile: EqProfile,
        route: OutputRoute,
    ): String = when (failure.reason) {
        com.weich.daptune.core.model.DapFailureReason.DOLBY_DISABLED -> "杜比已关闭 · 等待恢复"
        com.weich.daptune.core.model.DapFailureReason.NO_CONTROL -> "音效控制权被占用"
        else -> "${profile.name} 未应用 · ${route.displayName}"
    }

    private fun notify(text: String) {
        lastNotificationText = text
        getSystemService(NotificationManager::class.java).notify(NotificationId, notification(text))
    }

    private fun promoteToForeground(text: String) {
        lastNotificationText = text
        startForeground(NotificationId, notification(text))
    }

    private fun notification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EqAutomationService::class.java).setAction(ActionStop),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.ic_notification_equalizer)
            .setContentTitle("DapTune 自动切换")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                "自动切换",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "播放设备变化时应用对应的均衡器配置"
                setShowBadge(false)
            },
        )
    }

    private fun shutdown() {
        recoveryScheduler.onAutomationStopped()
        monitorJob?.cancel()
        monitorJob = null
        monitorReady = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun recordMonitorFailure(error: Throwable) {
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

    companion object {
        const val ActionStart = "com.weich.daptune.action.START_AUTOMATION"
        const val ActionStop = "com.weich.daptune.action.STOP_AUTOMATION"
        const val ActionRecover =
            "com.weich.daptune.action.RECOVER_AFTER_TASK_REMOVAL"
        private const val XiaomiDolbyStateAction =
            "miui.intent.action.ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH"
        private const val NotificationChannelId = "eq_automation"
        private const val NotificationId = 0xDA20
        private const val MonitorStartupTimeoutMillis = 6_000L
        private const val RouteOperationTimeoutMillis = 10_000L
    }
}
