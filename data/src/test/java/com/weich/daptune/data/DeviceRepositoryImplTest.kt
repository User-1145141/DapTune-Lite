package com.weich.daptune.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryImplTest {
    @Test
    fun forgettingDeviceRemovesBindingAppliedStateAndHistory() = runBlocking {
        val calls = mutableListOf<String>()
        val repository = DeviceRepositoryImpl(
            deviceDao = RecordingDeviceDao(calls),
            appliedStateDao = UnusedAppliedStateDao(),
        )

        repository.forgetRoute("device:usb:test")

        assertEquals(
            listOf(
                "binding:device:usb:test",
                "applied:device:usb:test",
                "history:device:usb:test",
            ),
            calls,
        )
    }

    @Test
    fun verifiedDeviceConsolidatesKnownLegacyKeysAndPreservesTheirRule() = runBlocking {
        val dao = InMemoryDeviceDao().apply {
            devices["legacy:anonymous"] = knownDevice("legacy:anonymous")
            devices["legacy:fallback"] = knownDevice("legacy:fallback")
            deviceBindings["legacy:anonymous"] = "custom.vivo"
            deviceBindings["legacy:fallback"] = "custom.vivo"
        }

        dao.rememberPersistentDevice(
            device = knownDevice("device:bluetooth:verified"),
            legacyRouteKeys = listOf("legacy:anonymous", "legacy:fallback"),
        )

        assertEquals(setOf("device:bluetooth:verified"), dao.devices.keys)
        assertEquals(
            mapOf("device:bluetooth:verified" to "custom.vivo"),
            dao.deviceBindings,
        )
    }

    @Test
    fun conflictingLegacyRulesAreNeverMergedOrDeleted() = runBlocking {
        val dao = InMemoryDeviceDao().apply {
            devices["legacy:first"] = knownDevice("legacy:first")
            devices["legacy:second"] = knownDevice("legacy:second")
            deviceBindings["legacy:first"] = "custom.first"
            deviceBindings["legacy:second"] = "custom.second"
        }

        dao.rememberPersistentDevice(
            device = knownDevice("device:bluetooth:verified"),
            legacyRouteKeys = listOf("legacy:first", "legacy:second"),
        )

        assertTrue("legacy:first" in dao.devices)
        assertTrue("legacy:second" in dao.devices)
        assertEquals(null, dao.deviceBindings["device:bluetooth:verified"])
    }

    private fun knownDevice(routeKey: String) = KnownDeviceEntity(
        routeKey = routeKey,
        displayName = "vivo TWS Air3 Pro",
        routeType = "BLUETOOTH",
        rawAddressPresent = true,
        lastSeenAtEpochMillis = 0L,
    )

    private class RecordingDeviceDao(
        private val calls: MutableList<String>,
    ) : DeviceDao {
        override fun observeKnownDevices(): Flow<List<KnownDeviceEntity>> = MutableStateFlow(emptyList())

        override fun observeBindings(): Flow<List<DeviceBindingEntity>> = MutableStateFlow(emptyList())

        override suspend fun getBoundProfileId(routeKey: String): String? = null

        override suspend fun getKnownDevices(routeKeys: List<String>): List<KnownDeviceEntity> =
            emptyList()

        override suspend fun getBindings(routeKeys: List<String>): List<DeviceBindingEntity> =
            emptyList()

        override suspend fun upsertDevice(device: KnownDeviceEntity) = Unit

        override suspend fun upsertBinding(binding: DeviceBindingEntity) = Unit

        override suspend fun deleteBinding(routeKey: String) {
            calls += "binding:$routeKey"
        }

        override suspend fun deleteKnownDevice(routeKey: String) {
            calls += "history:$routeKey"
        }

        override suspend fun deleteAppliedState(routeKey: String) {
            calls += "applied:$routeKey"
        }
    }

    private class UnusedAppliedStateDao : AppliedStateDao {
        override fun observe(): Flow<AppliedStateEntity?> = MutableStateFlow(null)

        override suspend fun get(): AppliedStateEntity? = null

        override suspend fun upsert(state: AppliedStateEntity) = Unit
    }

    private class InMemoryDeviceDao : DeviceDao {
        val devices = linkedMapOf<String, KnownDeviceEntity>()
        val deviceBindings = linkedMapOf<String, String>()

        override fun observeKnownDevices(): Flow<List<KnownDeviceEntity>> =
            MutableStateFlow(devices.values.toList())

        override fun observeBindings(): Flow<List<DeviceBindingEntity>> = MutableStateFlow(
            deviceBindings.map { (routeKey, profileId) ->
                DeviceBindingEntity(routeKey, profileId)
            },
        )

        override suspend fun getBoundProfileId(routeKey: String): String? =
            deviceBindings[routeKey]

        override suspend fun getKnownDevices(routeKeys: List<String>): List<KnownDeviceEntity> =
            routeKeys.mapNotNull(devices::get)

        override suspend fun getBindings(routeKeys: List<String>): List<DeviceBindingEntity> =
            routeKeys.mapNotNull { routeKey ->
                deviceBindings[routeKey]?.let { profileId ->
                    DeviceBindingEntity(routeKey, profileId)
                }
            }

        override suspend fun upsertDevice(device: KnownDeviceEntity) {
            devices[device.routeKey] = device
        }

        override suspend fun upsertBinding(binding: DeviceBindingEntity) {
            deviceBindings[binding.routeKey] = binding.profileId
        }

        override suspend fun deleteBinding(routeKey: String) {
            deviceBindings.remove(routeKey)
        }

        override suspend fun deleteKnownDevice(routeKey: String) {
            devices.remove(routeKey)
        }

        override suspend fun deleteAppliedState(routeKey: String) = Unit
    }
}
