package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteIdentityTest {
    @Test
    fun bluetoothAddress_isHashedAndNeverStoredInKey() {
        val address = "12:34:56:78:9A:BC"

        val route = RouteIdentity.create(
            type = OutputRouteType.BLUETOOTH,
            displayName = "Test Buds",
            address = address,
            fallbackIdentity = "unused",
        )

        assertTrue(route.rawAddressPresent)
        assertFalse(route.key.contains(address, ignoreCase = true))
        assertEquals(
            route.key,
            RouteIdentity.create(OutputRouteType.BLUETOOTH, "Renamed", address, "other").key,
        )
    }

    @Test
    fun devicesWithDifferentAddresses_getDifferentStableKeys() {
        val first = RouteIdentity.create(OutputRouteType.BLUETOOTH, "Buds", "AA", "")
        val second = RouteIdentity.create(OutputRouteType.BLUETOOTH, "Buds", "BB", "")

        assertNotEquals(first.key, second.key)
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
