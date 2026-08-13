package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteIdentityKind
import com.weich.daptune.core.model.OutputRouteType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RouteSelectionTest {
    @Test
    fun bluetoothWinsWhenPlatformAlsoReportsBuiltInSpeaker() {
        val bluetooth = OutputRoute(
            key = "device:bluetooth:test",
            displayName = "Test Buds",
            type = OutputRouteType.BLUETOOTH,
        )

        assertEquals(bluetooth, listOf(OutputRoute.Speaker, bluetooth).preferredOutputRoute())
    }

    @Test
    fun wiredAndUsbRoutesWinOverBuiltInSpeaker() {
        val wired = OutputRoute("wired:any", "有线耳机", OutputRouteType.WIRED_HEADSET)
        val usb = OutputRoute("device:usb:test", "USB DAC", OutputRouteType.USB)

        assertEquals(wired, listOf(OutputRoute.Speaker, wired).preferredOutputRoute())
        assertEquals(usb, listOf(OutputRoute.Speaker, wired, usb).preferredOutputRoute())
    }

    @Test
    fun mediaRouterProviderIdYieldsTheSameBluetoothHardwareAddress() {
        assertEquals(
            "44:FB:76:3D:E3:C3",
            extractBluetoothHardwareAddress(
                "com.android.server.media/.SystemMediaRoute2Provider:44:FB:76:3D:E3:C3",
            ),
        )
        assertNull(extractBluetoothHardwareAddress("ROUTE_ID_BUILTIN_SPEAKER"))
    }

    @Test
    fun verifiedIdentityWinsOverTransientIdentityForTheSameRouteType() {
        val transient = OutputRoute(
            key = "transient:bluetooth:test",
            displayName = "Test Buds",
            type = OutputRouteType.BLUETOOTH,
            identityKind = OutputRouteIdentityKind.TRANSIENT,
        )
        val persistent = OutputRoute(
            key = "device:bluetooth:test",
            displayName = "Test Buds",
            type = OutputRouteType.BLUETOOTH,
        )

        assertEquals(persistent, listOf(transient, persistent).preferredOutputRoute())
    }

    @Test
    fun anonymizedAddressCanBeExtractedButIsNotAcceptedAsHardwareIdentity() {
        val routeId =
            "com.android.server.media/.SystemMediaRoute2Provider:XX:XX:XX:XX:E3:C3"

        assertEquals("XX:XX:XX:XX:E3:C3", extractBluetoothReportedAddress(routeId))
        assertNull(extractBluetoothHardwareAddress(routeId))
    }
}
