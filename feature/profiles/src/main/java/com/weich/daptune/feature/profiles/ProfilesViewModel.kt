package com.weich.daptune.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.domain.DuplicateProfileUseCase
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
