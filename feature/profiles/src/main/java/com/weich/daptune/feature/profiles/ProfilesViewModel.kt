package com.weich.daptune.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.core.eq.OverflowMode
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.domain.DuplicateProfileUseCase
import com.weich.daptune.domain.ImportCurveUseCase
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
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<EqProfile> = emptyList(),
    val selectedProfileId: String = "builtin.flat",
) {
    val builtIns: List<EqProfile> get() = profiles.filter(EqProfile::isBuiltIn)
    val userProfiles: List<EqProfile> get() = profiles.filterNot(EqProfile::isBuiltIn)
}

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val duplicateProfile: DuplicateProfileUseCase,
    private val importCurve: ImportCurveUseCase,
    private val saveProfile: SaveProfileUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = mutableState
    private val messageChannel = Channel<String>(Channel.BUFFERED)
    val messages = messageChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            profileRepository.ensureBuiltIns()
            combine(profileRepository.profiles, settingsRepository.settings) { profiles, settings ->
                ProfilesUiState(profiles, settings.selectedProfileId)
            }.collect(mutableState::emit)
        }
    }

    fun select(profile: EqProfile) {
        viewModelScope.launch { settingsRepository.setSelectedProfile(profile.id) }
    }

    fun duplicate(profile: EqProfile) {
        viewModelScope.launch {
            runCatching { duplicateProfile(profile, profile.name) }
                .onSuccess { copy ->
                    settingsRepository.setSelectedProfile(copy.id)
                    messageChannel.send("已创建“${copy.name}”")
                }
                .onFailure { messageChannel.send(it.message ?: "复制失败") }
        }
    }

    fun importText(
        text: String,
        fileName: String,
        overflowMode: OverflowMode = OverflowMode.FIT,
    ) {
        viewModelScope.launch {
            runCatching {
                val imported = importCurve.parse(text, fileName)
                val curve = importCurve.convert(imported, overflowMode)
                val saved = saveProfile(
                    existingId = null,
                    name = imported.suggestedName,
                    curve = curve,
                    source = imported.source,
                )
                Triple(saved, imported.exceedsLimit, imported.warnings)
            }.onSuccess { (saved, adjusted, warnings) ->
                settingsRepository.setSelectedProfile(saved.id)
                val adjustment = if (adjusted) "；已按比例压缩到 +10 dB 上限" else ""
                val detail = warnings.joinToString(separator = "；", prefix = if (warnings.isEmpty()) "" else "；")
                messageChannel.send("已导入“${saved.name}”$adjustment$detail")
            }.onFailure { error ->
                messageChannel.send(error.message ?: "无法导入文件")
            }
        }
    }

    fun delete(profile: EqProfile) {
        if (profile.isBuiltIn) return
        viewModelScope.launch {
            runCatching { profileRepository.deleteProfile(profile.id) }
                .onSuccess {
                    if (mutableState.value.selectedProfileId == profile.id) {
                        settingsRepository.setSelectedProfile("builtin.flat")
                    }
                    messageChannel.send("已删除“${profile.name}”")
                }
                .onFailure { messageChannel.send(it.message ?: "删除失败") }
        }
    }
}
