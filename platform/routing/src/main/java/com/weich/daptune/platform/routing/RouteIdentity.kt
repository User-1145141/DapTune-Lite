package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteIdentityKind
import com.weich.daptune.core.model.OutputRouteType
import com.weich.daptune.core.model.normalizeRouteName
import java.security.MessageDigest
import java.util.Locale

/**
 * Creates privacy-preserving route keys.
 *
 * Bluetooth identities are persistent only when [address] is a complete hardware address that
 * has already been verified against Android's bonded-device inventory. Names, anonymized MACs and
 * MediaRouter provider IDs are deliberately transient: none of them uniquely identifies a device.
 */
internal object RouteIdentity {
    fun create(
        type: OutputRouteType,
        displayName: String,
        address: String,
        fallbackIdentity: String,
        legacyFallbackIdentities: Set<String> = emptySet(),
    ): OutputRoute {
        if (type == OutputRouteType.BUILT_IN_SPEAKER) return OutputRoute.Speaker
        if (type == OutputRouteType.WIRED_HEADSET) {
            return OutputRoute("wired:any", "有线耳机", OutputRouteType.WIRED_HEADSET)
        }

        val cleanName = displayName.trim().ifBlank { defaultName(type) }
        if (type.isBluetooth) {
            return createBluetooth(
                type = type,
                displayName = cleanName,
                verifiedHardwareAddress = address,
                fallbackIdentity = fallbackIdentity,
                legacyFallbackIdentities = legacyFallbackIdentities,
            )
        }

        val cleanAddress = address.trim()
        val identity = cleanAddress.ifBlank { fallbackIdentity.trim().ifBlank { cleanName } }
        return OutputRoute(
            key = routeKey(type.name.lowercase(Locale.ROOT), identity),
            displayName = cleanName,
            type = type,
            rawAddressPresent = cleanAddress.isNotBlank(),
        )
    }

    private fun createBluetooth(
        type: OutputRouteType,
        displayName: String,
        verifiedHardwareAddress: String,
        fallbackIdentity: String,
        legacyFallbackIdentities: Set<String>,
    ): OutputRoute {
        val hardwareAddress = normalizeBluetoothHardwareAddress(verifiedHardwareAddress)
        if (hardwareAddress == null) {
            val transientIdentity = normalizeRouteName(displayName)
                .ifBlank { fallbackIdentity.trim().lowercase(Locale.ROOT) }
            return OutputRoute(
                key = "transient:bluetooth:${digest(transientIdentity)}",
                displayName = displayName,
                type = type,
                rawAddressPresent = false,
                identityKind = OutputRouteIdentityKind.TRANSIENT,
            )
        }

        val currentKey = routeKey(BluetoothIdentityNamespace, hardwareAddress)
        val legacyIdentities = buildSet {
            // LE Audio used its transport type as a separate namespace in older builds.
            add(hardwareAddress)
            // Older builds hashed AudioDeviceInfo's privacy-redacted address as if it were a MAC.
            add(anonymizeBluetoothHardwareAddress(hardwareAddress))
            // Older builds preserved the case supplied by some vendor framework implementations.
            add(hardwareAddress.lowercase(Locale.ROOT))
            addAll(legacyFallbackIdentities.filter(String::isNotBlank))
        }
        val legacyKeys = buildSet {
            for (namespace in LegacyBluetoothIdentityNamespaces) {
                for (identity in legacyIdentities) add(routeKey(namespace, identity))
            }
        } - currentKey

        return OutputRoute(
            key = currentKey,
            displayName = displayName,
            type = type,
            rawAddressPresent = true,
            identityKind = OutputRouteIdentityKind.PERSISTENT,
            legacyKeys = legacyKeys,
        )
    }

    internal fun routeKey(namespace: String, identity: String): String =
        "device:$namespace:${digest("$namespace:$identity")}"

    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { byte -> "%02x".format(byte) }

    private fun defaultName(type: OutputRouteType): String = when (type) {
        OutputRouteType.BUILT_IN_SPEAKER -> "手机扬声器"
        OutputRouteType.WIRED_HEADSET -> "有线耳机"
        OutputRouteType.BLUETOOTH -> "蓝牙音频设备"
        OutputRouteType.BLE_AUDIO -> "LE Audio 设备"
        OutputRouteType.USB -> "USB 音频设备"
        OutputRouteType.HDMI -> "HDMI 音频设备"
        OutputRouteType.REMOTE -> "远程音频设备"
        OutputRouteType.UNKNOWN -> "音频设备"
    }

    private const val BluetoothIdentityNamespace = "bluetooth"
    private val LegacyBluetoothIdentityNamespaces = setOf("bluetooth", "ble_audio")
}

internal fun normalizeBluetoothHardwareAddress(value: String?): String? {
    val clean = value?.trim().orEmpty()
    if (!BluetoothMacAddress.matches(clean)) return null
    val normalized = clean.uppercase(Locale.ROOT)
    return normalized.takeUnless { it == ZeroBluetoothAddress || it == AndroidUnavailableAddress }
}

internal fun anonymizeBluetoothHardwareAddress(hardwareAddress: String): String =
    "XX:XX:XX:XX:${hardwareAddress.takeLast(5).uppercase(Locale.ROOT)}"

internal val OutputRouteType.isBluetooth: Boolean
    get() = this == OutputRouteType.BLUETOOTH || this == OutputRouteType.BLE_AUDIO

private const val ZeroBluetoothAddress = "00:00:00:00:00:00"
private const val AndroidUnavailableAddress = "02:00:00:00:00:00"
private val BluetoothMacAddress = Regex("^(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
