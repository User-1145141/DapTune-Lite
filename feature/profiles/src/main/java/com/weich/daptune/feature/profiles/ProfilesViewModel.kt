package com.weich.daptune.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.weich.daptune.core.eq.CurveImportFormat
import com.weich.daptune.core.eq.OverflowMode
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.domain.AudioRouteMonitor
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.DuplicateProfileUseCase
import com.weich.daptune.domain.ImportCurveUseCase
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.ResolveProfileForRouteUseCase
import com.weich.daptune.domain.SaveProfileUseCase
import com.weich.daptune.domain.SelectProfileForRouteUseCase
import com.weich.daptune.domain.SettingsRepository
import com.weich.daptune.domain.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfilesUiState(
    val profiles: List<EqProfile> = emptyList(),
    val selectedProfileId: String = "builtin.flat",
    val currentRoute: OutputRoute = OutputRoute.Speaker,
) {
    val builtIns: List<EqProfile> get() = profiles.filter(EqProfile::isBuiltIn)
    val userProfiles: List<EqProfile> get() = profiles.filterNot(EqProfile::isBuiltIn)
}

private const val MINIMUM_AUTO_EQ_QUERY_LENGTH = 2
private const val MAXIMUM_AUTO_EQ_QUERY_LENGTH = 120
private const val AUTO_EQ_SEARCH_DEBOUNCE_MILLIS = 300L

private fun DapApplyResult.selectionMessage(profileName: String): String = when (this) {
    is DapApplyResult.Success -> "已切换并应用“$profileName”"
    is DapApplyResult.Failure -> "已切换到“$profileName”，但未能应用：$detail"
}
