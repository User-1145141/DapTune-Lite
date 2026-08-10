package com.weich.daptune.feature.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.AppSettings
import com.weich.daptune.core.model.DeviceBinding
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.KnownOutputDevice
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.domain.AudioRouteMonitor
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class AutomationUiState(
    val settings: AppSettings = AppSettings(),
    val profiles: List<EqProfile> = emptyList(),
    val devices: List<KnownOutputDevice> = emptyList(),
    val bindings: List<DeviceBinding> = emptyList(),
    val currentRoute: OutputRoute = OutputRoute.Speaker,
    val appliedSnapshot: AppliedSnapshot? = null,
) {
    fun profileFor(routeKey: String): EqProfile? {
        val profileId = bindings.firstOrNull { it.routeKey == routeKey }?.profileId
        return profiles.firstOrNull { it.id == profileId }
    }

    val defaultProfile: EqProfile?
        get() = profiles.firstOrNull { it.id == settings.defaultProfileId }
}

private data class AutomationSources(
    val profiles: List<EqProfile>,
    val devices: List<KnownOutputDevice>,
    val bindings: List<DeviceBinding>,
    val settings: AppSettings,
    val route: OutputRoute,
    val snapshot: AppliedSnapshot?,
)

private data class ProfileDeviceSources(
    val profiles: List<EqProfile>,
    val devices: List<KnownOutputDevice>,
    val bindings: List<DeviceBinding>,
)

private data class RuntimeSources(
    val settings: AppSettings,
    val route: OutputRoute,
    val snapshot: AppliedSnapshot?,
)

@HiltViewModel
class AutomationViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
    private val routeMonitor: AudioRouteMonitor,
    private val controller: AutomationController,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AutomationUiState())
    val state: StateFlow<AutomationUiState> = mutableState
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            profileRepository.ensureBuiltIns()
            val profileDeviceSources = combine(
                profileRepository.profiles,
                deviceRepository.knownDevices,
                deviceRepository.bindings,
            ) { profiles, devices, bindings ->
                ProfileDeviceSources(profiles, devices, bindings)
            }
            val runtimeSources = combine(
                settingsRepository.settings,
                routeMonitor.routes,
                deviceRepository.appliedSnapshot,
            ) { settings, route, snapshot ->
                RuntimeSources(settings, route, snapshot)
            }
            combine(profileDeviceSources, runtimeSources) { profileSources, runtime ->
                AutomationSources(
                    profiles = profileSources.profiles,
                    devices = profileSources.devices,
                    bindings = profileSources.bindings,
                    settings = runtime.settings,
                    route = runtime.route,
                    snapshot = runtime.snapshot,
                )
            }.collect { sources ->
                mutableState.emit(
                    AutomationUiState(
                        settings = sources.settings,
                        profiles = sources.profiles,
                        devices = sources.devices,
                        bindings = sources.bindings,
                        currentRoute = sources.route,
                        appliedSnapshot = sources.snapshot,
                    ),
                )
                deviceRepository.rememberRoute(sources.route)
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                settingsRepository.setAutomationEnabled(enabled)
                if (enabled) controller.start() else controller.stop()
            }.onFailure { messageChannel.send(it.message ?: "无法更新自动切换") }
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        setEnabled(true)
        if (!granted) {
            messageChannel.trySend("通知已关闭；自动切换仍会在系统的活动应用中运行")
        }
    }

    fun setApplyAtBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setApplyAtBoot(enabled) }
    }

    fun setDefaultProfile(profileId: String) {
        viewModelScope.launch {
            settingsRepository.setDefaultProfile(profileId)
            if (mutableState.value.settings.automationEnabled) controller.refresh()
        }
    }

    fun bind(routeKey: String, profileId: String?) {
        viewModelScope.launch {
            deviceRepository.bind(routeKey, profileId)
            messageChannel.send(if (profileId == null) "已改为跟随默认配置" else "设备配置已更新")
            if (mutableState.value.settings.automationEnabled && routeKey == mutableState.value.currentRoute.key) {
                controller.refresh()
            }
        }
    }
}
