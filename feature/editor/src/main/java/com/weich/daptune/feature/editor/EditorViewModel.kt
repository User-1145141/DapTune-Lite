package com.weich.daptune.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.core.eq.EqTransforms
import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.DapCapability
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.ProfileSource
import com.weich.daptune.domain.ApplyCurveUseCase
import com.weich.daptune.domain.AudioRouteMonitor
import com.weich.daptune.domain.DapGateway
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.SaveProfileUseCase
import com.weich.daptune.domain.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val profiles: List<EqProfile> = emptyList(),
    val selectedProfileId: String = "builtin.flat",
    val loadedProfileId: String? = null,
    val curve: EqCurve = EqCurve.flat(),
    val originalCurve: EqCurve = EqCurve.flat(),
    val selectedBand: Int = 0,
    val route: OutputRoute = OutputRoute.Speaker,
    val capability: DapCapability? = null,
    val appliedSnapshot: AppliedSnapshot? = null,
    val isApplying: Boolean = false,
    val isInspecting: Boolean = false,
) {
    val selectedProfile: EqProfile?
        get() = profiles.firstOrNull { it.id == selectedProfileId }

    val isDirty: Boolean
        get() = curve != originalCurve

    val canApply: Boolean
        get() = capability?.isReady == true && !isApplying
}

enum class CurveAction {
    PEAK_TO_ZERO,
    MEAN_TO_ZERO,
    SMOOTH,
    INVERT,
    FLATTEN,
}

sealed interface EditorEvent {
    data class Message(val text: String) : EditorEvent
    data class SuggestSaveName(val name: String) : EditorEvent
}

private data class EditorSources(
    val profiles: List<EqProfile>,
    val selectedProfileId: String,
    val route: OutputRoute,
    val snapshot: AppliedSnapshot?,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val deviceRepository: DeviceRepository,
    private val routeMonitor: AudioRouteMonitor,
    private val dapGateway: DapGateway,
    private val applyCurve: ApplyCurveUseCase,
    private val saveProfile: SaveProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = mutableState

    private val eventChannel = Channel<EditorEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            profileRepository.ensureBuiltIns()
            combine(
                profileRepository.profiles,
                settingsRepository.settings,
                routeMonitor.routes,
                deviceRepository.appliedSnapshot,
            ) { profiles, settings, route, snapshot ->
                EditorSources(profiles, settings.selectedProfileId, route, snapshot)
            }.collect(::updateSources)
        }
        refreshCapability()
    }

    fun selectProfile(profileId: String) {
        val profile = mutableState.value.profiles.firstOrNull { it.id == profileId } ?: return
        mutableState.update {
            it.copy(
                selectedProfileId = profile.id,
                loadedProfileId = profile.id,
                curve = profile.curve,
                originalCurve = profile.curve,
            )
        }
        viewModelScope.launch { settingsRepository.setSelectedProfile(profile.id) }
    }

    fun selectBand(index: Int) {
        mutableState.update { it.copy(selectedBand = index.coerceIn(0, 19)) }
    }

    fun setGain(index: Int, valueQ4: Int) {
        mutableState.update {
            it.copy(
                curve = it.curve.withGainQ4(index, valueQ4.coerceIn(-EqCurve.MAX_GAIN_Q4, EqCurve.MAX_GAIN_Q4)),
                selectedBand = index,
            )
        }
    }

    fun resetChanges() {
        mutableState.update {
            it.copy(curve = it.originalCurve)
        }
    }

    fun transform(action: CurveAction) {
        mutableState.update { current ->
            val transformed = when (action) {
                CurveAction.PEAK_TO_ZERO -> EqTransforms.peakToZero(current.curve)
                CurveAction.MEAN_TO_ZERO -> EqTransforms.meanToZero(current.curve)
                CurveAction.SMOOTH -> EqTransforms.smooth(current.curve)
                CurveAction.INVERT -> EqTransforms.invert(current.curve)
                CurveAction.FLATTEN -> EqCurve.flat()
            }
            current.copy(curve = transformed)
        }
    }

    fun save(name: String, overwrite: Boolean) {
        val current = mutableState.value
        viewModelScope.launch {
            runCatching {
                saveProfile(
                    existingId = if (overwrite) current.selectedProfile?.takeUnless(EqProfile::isBuiltIn)?.id else null,
                    name = name,
                    curve = current.curve,
                    source = current.selectedProfile?.source?.takeIf { overwrite }
                        ?: ProfileSource.MANUAL,
                )
            }.onSuccess { saved ->
                settingsRepository.setSelectedProfile(saved.id)
                mutableState.update {
                    it.copy(
                        selectedProfileId = saved.id,
                        loadedProfileId = saved.id,
                        curve = saved.curve,
                        originalCurve = saved.curve,
                    )
                }
                eventChannel.send(EditorEvent.Message("已保存“${saved.name}”"))
            }.onFailure { error ->
                eventChannel.send(EditorEvent.Message(error.message ?: "保存失败"))
            }
        }
    }

    fun requestSave() {
        val current = mutableState.value
        val profile = current.selectedProfile
        eventChannel.trySend(
            EditorEvent.SuggestSaveName(
                profile?.name ?: "新配置",
            ),
        )
    }

    fun apply() {
        val current = mutableState.value
        if (current.isApplying) return
        mutableState.update { it.copy(isApplying = true) }
        viewModelScope.launch {
            val result = applyCurve(
                profileId = current.selectedProfileId,
                curve = current.curve,
                route = current.route,
            )
            mutableState.update { it.copy(isApplying = false) }
            when (result) {
                is DapApplyResult.Success -> eventChannel.send(
                    EditorEvent.Message("已写入并验证 ${result.receipt.profileCount} 个 Dolby 配置"),
                )
                is DapApplyResult.Failure -> eventChannel.send(EditorEvent.Message(result.detail))
            }
            refreshCapability()
        }
    }

    fun refreshCapability() {
        if (mutableState.value.isInspecting) return
        mutableState.update { it.copy(isInspecting = true) }
        viewModelScope.launch {
            val capability = dapGateway.inspect()
            mutableState.update { it.copy(capability = capability, isInspecting = false) }
        }
    }

    private fun updateSources(sources: EditorSources) {
        mutableState.update { current ->
            val selected = sources.profiles.firstOrNull { it.id == sources.selectedProfileId }
                ?: sources.profiles.firstOrNull()
            val selectionChanged = selected != null && current.loadedProfileId != selected.id
            if (selectionChanged) {
                current.copy(
                    profiles = sources.profiles,
                    selectedProfileId = selected.id,
                    loadedProfileId = selected.id,
                    curve = selected.curve,
                    originalCurve = selected.curve,
                    route = sources.route,
                    appliedSnapshot = sources.snapshot,
                )
            } else {
                current.copy(
                    profiles = sources.profiles,
                    selectedProfileId = selected?.id ?: current.selectedProfileId,
                    route = sources.route,
                    appliedSnapshot = sources.snapshot,
                )
            }
        }
        viewModelScope.launch { deviceRepository.rememberRoute(sources.route) }
    }
}
