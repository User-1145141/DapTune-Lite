package com.weich.daptune.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private class RecordingDeviceDao(
        private val calls: MutableList<String>,
    ) : DeviceDao {
        override fun observeKnownDevices(): Flow<List<KnownDeviceEntity>> = MutableStateFlow(emptyList())

        override fun observeBindings(): Flow<List<DeviceBindingEntity>> = MutableStateFlow(emptyList())

        override suspend fun getBoundProfileId(routeKey: String): String? = null

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
}
