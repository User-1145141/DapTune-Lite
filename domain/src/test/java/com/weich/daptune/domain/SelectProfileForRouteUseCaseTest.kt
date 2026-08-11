package com.weich.daptune.domain

import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.AppSettings
import com.weich.daptune.core.model.DeviceBinding
import com.weich.daptune.core.model.KnownOutputDevice
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class SelectProfileForRouteUseCaseTest {
    @Test
    fun selectionUpdatesCurrentRouteBindingBeforeEditorSelection() = runBlocking {
        val calls = mutableListOf<String>()
        val deviceRepository = RecordingDeviceRepository(calls)
        val settingsRepository = RecordingSettingsRepository(calls)
        val route = OutputRoute(
            key = "bluetooth:headphones",
            displayName = "Headphones",
            type = OutputRouteType.BLUETOOTH,
        )

        SelectProfileForRouteUseCase(deviceRepository, settingsRepository)(
            profileId = "custom.reference",
            route = route,
        )

        assertEquals(route, deviceRepository.rememberedRoute)
        assertEquals(route.key to "custom.reference", deviceRepository.binding)
        assertEquals("custom.reference", settingsRepository.selectedProfileId)
        assertEquals(
            listOf(
                "remember:${route.key}",
                "bind:${route.key}:custom.reference",
                "select:custom.reference",
            ),
            calls,
        )
    }

    private class RecordingDeviceRepository(
        private val calls: MutableList<String>,
    ) : DeviceRepository {
        override val knownDevices: Flow<List<KnownOutputDevice>> = MutableStateFlow(emptyList())
        override val bindings: Flow<List<DeviceBinding>> = MutableStateFlow(emptyList())
        override val appliedSnapshot: Flow<AppliedSnapshot?> = MutableStateFlow(null)

        var rememberedRoute: OutputRoute? = null
        var binding: Pair<String, String?>? = null

        override suspend fun rememberRoute(route: OutputRoute) {
            rememberedRoute = route
            calls += "remember:${route.key}"
        }

        override suspend fun bind(routeKey: String, profileId: String?) {
            binding = routeKey to profileId
            calls += "bind:$routeKey:$profileId"
        }

        override suspend fun getBoundProfileId(routeKey: String): String? = null

        override suspend fun updateAppliedSnapshot(snapshot: AppliedSnapshot) = Unit

        override suspend fun markAppliedStateStale() = Unit
    }

    private class RecordingSettingsRepository(
        private val calls: MutableList<String>,
    ) : SettingsRepository {
        private val mutableSettings = MutableStateFlow(AppSettings())
        override val settings: Flow<AppSettings> = mutableSettings

        var selectedProfileId: String? = null

        override suspend fun setSelectedProfile(profileId: String) {
            selectedProfileId = profileId
            mutableSettings.value = mutableSettings.value.copy(selectedProfileId = profileId)
            calls += "select:$profileId"
        }

        override suspend fun setDefaultProfile(profileId: String) {
            mutableSettings.value = mutableSettings.value.copy(defaultProfileId = profileId)
        }

        override suspend fun setAutomationEnabled(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(automationEnabled = enabled)
        }

        override suspend fun setApplyAtBoot(enabled: Boolean) {
            mutableSettings.value = mutableSettings.value.copy(applyAtBoot = enabled)
        }
    }
}
