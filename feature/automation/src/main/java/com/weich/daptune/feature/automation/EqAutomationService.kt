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
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.domain.ApplyProfileUseCase
import com.weich.daptune.domain.AudioRouteMonitor
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.ResolveProfileForRouteUseCase
import com.weich.daptune.domain.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class EqAutomationService : Service() {
    @Inject lateinit var profileRepository: ProfileRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var deviceRepository: DeviceRepository
    @Inject lateinit var routeMonitor: AudioRouteMonitor
    @Inject lateinit var resolveProfile: ResolveProfileForRouteUseCase
    @Inject lateinit var applyProfile: ApplyProfileUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var monitorJob: Job? = null
    private var lastAppliedRouteKey: String? = null
    private var lastAppliedCurveHash: Int? = null
    private val dolbyStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (monitorJob?.isActive != true) return
            serviceScope.launch {
                processRoute(routeMonitor.currentRoute(), force = true)
            }
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
            serviceScope.launch {
                settingsRepository.setAutomationEnabled(false)
                shutdown()
            }
            return START_NOT_STICKY
        }
        startForeground(NotificationId, notification("等待播放设备"))
        if (intent?.action == ActionRefresh) {
            monitorJob?.cancel()
            lastAppliedRouteKey = null
            lastAppliedCurveHash = null
            monitorJob = startMonitoring()
        } else if (monitorJob?.isActive != true) {
            monitorJob = startMonitoring()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(dolbyStateReceiver) }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startMonitoring(): Job = serviceScope.launch {
        profileRepository.ensureBuiltIns()
        if (!settingsRepository.settings.first().automationEnabled) {
            shutdown()
            return@launch
        }

        // Resolve synchronously once so a stale StateFlow placeholder cannot apply the speaker profile.
        processRoute(routeMonitor.currentRoute(), force = true)
        routeMonitor.routes.drop(1).collect { route -> processRoute(route, force = false) }
    }

    private suspend fun processRoute(route: OutputRoute, force: Boolean) {
        if (!settingsRepository.settings.first().automationEnabled) {
            shutdown()
            return
        }
        deviceRepository.rememberRoute(route)
        val profile = resolveProfile(route) ?: return
        val curveHash = profile.curve.stableHash()
        if (!force && route.key == lastAppliedRouteKey && curveHash == lastAppliedCurveHash) return

        notify("正在应用 ${profile.name} · ${route.displayName}")
        when (val result = applyProfile(profile.id, route)) {
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
        getSystemService(NotificationManager::class.java).notify(NotificationId, notification(text))
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
        monitorJob?.cancel()
        monitorJob = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        const val ActionStart = "com.weich.daptune.action.START_AUTOMATION"
        const val ActionStop = "com.weich.daptune.action.STOP_AUTOMATION"
        const val ActionRefresh = "com.weich.daptune.action.REFRESH_AUTOMATION"
        private const val XiaomiDolbyStateAction =
            "miui.intent.action.ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH"
        private const val NotificationChannelId = "eq_automation"
        private const val NotificationId = 0xDA20
    }
}
