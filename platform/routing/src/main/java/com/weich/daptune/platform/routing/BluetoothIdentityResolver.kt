package com.weich.daptune.platform.routing

import android.annotation.SuppressLint
import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.weich.daptune.core.model.normalizeRouteName

/** Resolves framework routing metadata to a real, locally bonded Bluetooth identity. */
internal class BluetoothIdentityResolver(context: Context) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    @SuppressLint("MissingPermission")
    fun resolve(reportedAddress: String?, displayName: String): String? {
        if (!hasConnectPermission()) return null
        val bondedDevices = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        return resolveBluetoothHardwareAddress(
            reportedAddress = reportedAddress,
            displayName = displayName,
            bondedDevices = bondedDevices.mapNotNull(::toBondedIdentity),
        )
    }

    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun toBondedIdentity(device: BluetoothDevice): BondedBluetoothIdentity? {
        val hardwareAddress = runCatching { device.address }.getOrNull() ?: return null
        val names = buildSet {
            runCatching { device.name }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            runCatching { device.alias }.getOrNull()?.takeIf(String::isNotBlank)?.let(::add)
        }
        return BondedBluetoothIdentity(hardwareAddress, names)
    }
}

internal data class BondedBluetoothIdentity(
    val hardwareAddress: String,
    val names: Set<String>,
)

/**
 * Android's audio server commonly exposes `XX:XX:XX:XX:AA:BB`. The suffix is resolved only when
 * exactly one bonded device matches both it and the route name; ambiguity is rejected.
 */
internal fun resolveBluetoothHardwareAddress(
    reportedAddress: String?,
    displayName: String,
    bondedDevices: List<BondedBluetoothIdentity>,
): String? {
    val reported = reportedAddress?.trim().orEmpty()
    normalizeBluetoothHardwareAddress(reported)?.let { completeAddress ->
        return bondedDevices.asSequence()
            .mapNotNull { normalizeBluetoothHardwareAddress(it.hardwareAddress) }
            .singleOrNull { it == completeAddress }
    }

    val suffix = AnonymizedBluetoothAddress.matchEntire(reported)?.groupValues?.get(1)
        ?.uppercase()
        ?: return null
    val normalizedRouteName = normalizeRouteName(displayName)
    if (normalizedRouteName.isBlank()) return null

    return bondedDevices.asSequence()
        .mapNotNull { bonded ->
            val address = normalizeBluetoothHardwareAddress(bonded.hardwareAddress) ?: return@mapNotNull null
            val nameMatches = bonded.names.any { name ->
                normalizeRouteName(name) == normalizedRouteName
            }
            address.takeIf { it.endsWith(suffix) && nameMatches }
        }
        .distinct()
        .singleOrNull()
}

private val AnonymizedBluetoothAddress =
    Regex("(?i)^XX:XX:XX:XX:([0-9a-f]{2}:[0-9a-f]{2})$")
