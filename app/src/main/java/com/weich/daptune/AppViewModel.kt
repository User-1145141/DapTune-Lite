package com.weich.daptune

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.SettingsRepository
import com.weich.daptune.feature.automation.AutomationController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
    private val automationController: AutomationController,
) : ViewModel() {
    init {
        viewModelScope.launch { profileRepository.ensureBuiltIns() }
    }

    fun restoreAutomation() {
        viewModelScope.launch {
            if (settingsRepository.settings.first().automationEnabled) automationController.start()
        }
    }
}
