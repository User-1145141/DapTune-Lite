package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRouteIdentityKind
import com.weich.daptune.core.model.OutputRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIdentityTest {
    @Test
    fun verifiedBluetoothAddress_isHashedAndNeverStoredInKey() {
        val address = "12:34:56:78:9A:BC"

        val route = RouteIdentity.create(
            type = OutputRouteType.BLUETOOTH,
            displayName = "Test Buds",
            address = address,
            fallbackIdentity = "unused",
        )

        assertTrue(route.rawAddressPresent)
        assertEquals(OutputRouteIdentityKind.PERSISTENT, route.identityKind)
        assertFalse(route.key.contains(address, ignoreCase = true))
        assertEquals(
            route.key,
            RouteIdentity.create(OutputRouteType.BLUETOOTH, "Renamed", address, "other").key,
        )
    }

    @Test
    fun devicesWithDifferentVerifiedAddresses_getDifferentStableKeys() {
        val first = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Buds",
            "12:34:56:78:9A:BC",
            "",
        )
        val second = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Buds",
            "12:34:56:78:9A:BD",
            "",
        )

        assertNotEquals(first.key, second.key)
    }

    @Test
    fun bluetoothMacAddressCaseDoesNotChangeIdentity() {
        val upper = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Buds",
            "44:FB:76:3D:E3:C3",
            "",
        )
        val lower = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Buds",
            "44:fb:76:3d:e3:c3",
            "",
        )

        assertEquals(upper.key, lower.key)
    }

    @Test
    fun bluetoothAndLeAudio_sharePhysicalDeviceIdentity() {
        val classic = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Buds",
            "44:FB:76:3D:E3:C3",
            "",
        )
        val leAudio = RouteIdentity.create(
            OutputRouteType.BLE_AUDIO,
            "Buds",
            "44:FB:76:3D:E3:C3",
            "",
        )

        assertEquals(classic.key, leAudio.key)
    }

    @Test
    fun anonymizedOrMissingBluetoothAddress_isTransientAndNeverPersistable() {
        val anonymized = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Test Buds",
            "XX:XX:XX:XX:9A:BC",
            "provider-id",
        )
        val missing = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Test Buds",
            "",
            "different-provider-id",
        )

        assertEquals(anonymized.key, missing.key)
        assertEquals(OutputRouteIdentityKind.TRANSIENT, anonymized.identityKind)
        assertFalse(anonymized.rawAddressPresent)
        assertTrue(anonymized.key.startsWith("transient:bluetooth:"))
    }

    @Test
    fun persistentBluetoothRoute_carriesExactLegacyCandidates() {
        val route = RouteIdentity.create(
            OutputRouteType.BLUETOOTH,
            "Test Buds",
            "44:FB:76:3D:E3:C3",
            "unused",
            legacyFallbackIdentities = setOf("8:Test Buds"),
        )

        assertTrue(
            RouteIdentity.routeKey("bluetooth", "XX:XX:XX:XX:E3:C3") in route.legacyKeys,
        )
        assertTrue(RouteIdentity.routeKey("bluetooth", "8:Test Buds") in route.legacyKeys)
    }

    @Test
    fun speakerAndWiredRoutes_usePortableFixedKeys() {
        val speaker = RouteIdentity.create(OutputRouteType.BUILT_IN_SPEAKER, "Speaker", "raw", "id")
        val wired = RouteIdentity.create(OutputRouteType.WIRED_HEADSET, "Headset", "raw", "id")

        assertEquals("builtin:speaker", speaker.key)
        assertEquals("wired:any", wired.key)
        assertFalse(wired.rawAddressPresent)
    }
}
