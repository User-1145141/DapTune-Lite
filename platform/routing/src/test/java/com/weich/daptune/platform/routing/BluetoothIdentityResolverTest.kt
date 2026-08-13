package com.weich.daptune.platform.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothIdentityResolverTest {
    private val bondedDevices = listOf(
        BondedBluetoothIdentity(
            hardwareAddress = "44:FB:76:3D:E3:C3",
            names = setOf("vivo TWS Air3 Pro"),
        ),
        BondedBluetoothIdentity(
            hardwareAddress = "20:24:01:02:1A:4D",
            names = setOf("StarRing Ultra"),
        ),
    )

    @Test
    fun completeAddress_mustExistInBondedInventory() {
        assertEquals(
            "44:FB:76:3D:E3:C3",
            resolveBluetoothHardwareAddress(
                "44:fb:76:3d:e3:c3",
                "renamed route",
                bondedDevices,
            ),
        )
        assertNull(
            resolveBluetoothHardwareAddress(
                "12:34:56:78:9A:BC",
                "vivo TWS Air3 Pro",
                bondedDevices,
            ),
        )
    }

    @Test
    fun anonymizedAddress_resolvesOnlyWithUniqueSuffixAndNameMatch() {
        assertEquals(
            "44:FB:76:3D:E3:C3",
            resolveBluetoothHardwareAddress(
                "XX:XX:XX:XX:E3:C3",
                "vivo TWS Air3 Pro",
                bondedDevices,
            ),
        )
        assertNull(
            resolveBluetoothHardwareAddress(
                "XX:XX:XX:XX:E3:C3",
                "StarRing Ultra",
                bondedDevices,
            ),
        )
    }

    @Test
    fun ambiguousAnonymizedAddress_isRejected() {
        val ambiguous = bondedDevices + BondedBluetoothIdentity(
            hardwareAddress = "10:20:30:40:E3:C3",
            names = setOf("vivo TWS Air3 Pro"),
        )

        assertNull(
            resolveBluetoothHardwareAddress(
                "XX:XX:XX:XX:E3:C3",
                "vivo TWS Air3 Pro",
                ambiguous,
            ),
        )
    }
}
