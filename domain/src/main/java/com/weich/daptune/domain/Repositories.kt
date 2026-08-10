package com.weich.daptune.domain

import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.AppSettings
import com.weich.daptune.core.model.DeviceBinding
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.KnownOutputDevice
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.ProfileSource
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    val profiles: Flow<List<EqProfile>>

    suspend fun ensureBuiltIns()

    suspend fun getProfile(id: String): EqProfile?

    suspend fun saveUserProfile(
        id: String?,
        name: String,
        curve: EqCurve,
        source: ProfileSource,
    ): EqProfile

    suspend fun deleteProfile(id: String)
}

interface DeviceRepository {
    val knownDevices: Flow<List<KnownOutputDevice>>
    val bindings: Flow<List<DeviceBinding>>
    val appliedSnapshot: Flow<AppliedSnapshot?>

    suspend fun rememberRoute(route: OutputRoute)

    suspend fun bind(routeKey: String, profileId: String?)

    suspend fun getBoundProfileId(routeKey: String): String?

    suspend fun updateAppliedSnapshot(snapshot: AppliedSnapshot)

    suspend fun markAppliedStateStale()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setSelectedProfile(profileId: String)

    suspend fun setDefaultProfile(profileId: String)

    suspend fun setAutomationEnabled(enabled: Boolean)

    suspend fun setApplyAtBoot(enabled: Boolean)
}
