package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteType
import java.security.MessageDigest
import java.util.Locale

internal object RouteIdentity {
    fun create(
        type: OutputRouteType,
        displayName: String,
        address: String,
        fallbackIdentity: String,
    ): OutputRoute {
        if (type == OutputRouteType.BUILT_IN_SPEAKER) return OutputRoute.Speaker
        if (type == OutputRouteType.WIRED_HEADSET) {
            return OutputRoute("wired:any", "有线耳机", OutputRouteType.WIRED_HEADSET)
        }
        val cleanName = displayName.trim().ifBlank { defaultName(type) }
        val rawIdentity = address.trim().ifBlank { fallbackIdentity.trim().ifBlank { cleanName } }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${type.name.lowercase(Locale.ROOT)}:$rawIdentity".toByteArray(Charsets.UTF_8))
            .take(12)
            .joinToString("") { byte -> "%02x".format(byte) }
        return OutputRoute(
            key = "device:${type.name.lowercase(Locale.ROOT)}:$digest",
            displayName = cleanName,
            type = type,
            rawAddressPresent = address.isNotBlank(),
        )
    }

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
}
