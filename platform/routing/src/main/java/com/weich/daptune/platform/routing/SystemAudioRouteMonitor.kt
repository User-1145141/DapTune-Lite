package com.weich.daptune.platform.routing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRoute2Info
import android.media.MediaRouter2
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteType
import com.weich.daptune.domain.AudioRouteMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

@Singleton
class SystemAudioRouteMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioRouteMonitor {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaRouter = MediaRouter2.getInstance(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override val routes: Flow<OutputRoute> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val audioCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                trySend(Unit)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                trySend(Unit)
            }
        }
        val controllerCallback = object : MediaRouter2.ControllerCallback() {
            override fun onControllerUpdated(controller: MediaRouter2.RoutingController) {
                if (controller === mediaRouter.systemController) trySend(Unit)
            }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(ActionA2dpActiveDeviceChanged)
            addAction(ActionHeadsetActiveDeviceChanged)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(XiaomiDolbyStateAction)
        }
        audioManager.registerAudioDeviceCallback(audioCallback, handler)
        mediaRouter.registerControllerCallback(context.mainExecutor, controllerCallback)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        trySend(Unit)
        awaitClose {
            audioManager.unregisterAudioDeviceCallback(audioCallback)
            mediaRouter.unregisterControllerCallback(controllerCallback)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }
        .onStart { emit(Unit) }
        .debounce(RouteSettleMillis)
        .mapLatest { resolveCurrentRoute() }
        .distinctUntilChanged()
        .stateIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = OutputRoute.Speaker,
        )

    override suspend fun currentRoute(): OutputRoute = withContext(Dispatchers.Default) {
        resolveCurrentRoute()
    }

    private fun resolveCurrentRoute(): OutputRoute {
        val routedDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { audioManager.getAudioDevicesForAttributes(MediaAttributes) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        routedDevices
            .mapNotNull(::fromAudioDevice)
            .maxByOrNull { priority(it.type) }
            ?.let { return it }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaRouter.systemController.selectedRoutes
                .mapNotNull(::fromMediaRoute)
                .maxByOrNull { priority(it.type) }
                ?.let { return it }
        }

        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .mapNotNull(::fromAudioDevice)
            .maxByOrNull { priority(it.type) }
            ?: OutputRoute.Speaker
    }

    private fun fromAudioDevice(device: AudioDeviceInfo): OutputRoute? {
        if (!device.isSink) return null
        val type = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            -> OutputRouteType.BUILT_IN_SPEAKER

            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE,
            -> OutputRouteType.WIRED_HEADSET

            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HEARING_AID,
            -> OutputRouteType.BLUETOOTH

            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_BLE_BROADCAST,
            -> OutputRouteType.BLE_AUDIO

            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            -> OutputRouteType.USB

            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC,
            -> OutputRouteType.HDMI

            AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> OutputRouteType.REMOTE
            else -> return null
        }
        return RouteIdentity.create(
            type = type,
            displayName = device.productName?.toString().orEmpty(),
            address = device.address,
            fallbackIdentity = "${device.type}:${device.id}:${device.productName}",
        )
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun fromMediaRoute(route: MediaRoute2Info): OutputRoute? {
        val type = when (route.type) {
            MediaRoute2Info.TYPE_BUILTIN_SPEAKER -> OutputRouteType.BUILT_IN_SPEAKER
            MediaRoute2Info.TYPE_WIRED_HEADSET,
            MediaRoute2Info.TYPE_WIRED_HEADPHONES,
            -> OutputRouteType.WIRED_HEADSET

            MediaRoute2Info.TYPE_BLUETOOTH_A2DP,
            MediaRoute2Info.TYPE_HEARING_AID,
            -> OutputRouteType.BLUETOOTH

            MediaRoute2Info.TYPE_BLE_HEADSET -> OutputRouteType.BLE_AUDIO
            MediaRoute2Info.TYPE_USB_DEVICE,
            MediaRoute2Info.TYPE_USB_ACCESSORY,
            MediaRoute2Info.TYPE_USB_HEADSET,
            -> OutputRouteType.USB

            MediaRoute2Info.TYPE_HDMI,
            MediaRoute2Info.TYPE_HDMI_ARC,
            MediaRoute2Info.TYPE_HDMI_EARC,
            -> OutputRouteType.HDMI

            else -> return null
        }
        return RouteIdentity.create(
            type = type,
            displayName = route.name.toString(),
            address = "",
            fallbackIdentity = route.id,
        )
    }

    private fun priority(type: OutputRouteType): Int = when (type) {
        OutputRouteType.BLUETOOTH,
        OutputRouteType.BLE_AUDIO,
        -> 700
        OutputRouteType.USB -> 600
        OutputRouteType.WIRED_HEADSET -> 500
        OutputRouteType.HDMI -> 400
        OutputRouteType.BUILT_IN_SPEAKER -> 300
        OutputRouteType.REMOTE -> 200
        OutputRouteType.UNKNOWN -> 0
    }

    private companion object {
        const val RouteSettleMillis = 400L
        // These stable platform actions are hidden from parts of the public SDK surface.
        const val ActionA2dpActiveDeviceChanged =
            "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED"
        const val ActionHeadsetActiveDeviceChanged =
            "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED"
        const val XiaomiDolbyStateAction = "miui.intent.action.ACTION_SYSTEM_UI_DOLBY_EFFECT_SWITCH"
        val MediaAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
