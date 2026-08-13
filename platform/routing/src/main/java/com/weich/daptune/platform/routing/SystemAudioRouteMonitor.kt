package com.weich.daptune.platform.routing

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
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
import android.media.RouteDiscoveryPreference
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteIdentityKind
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext

@Singleton
class SystemAudioRouteMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioRouteMonitor {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mediaRouter = MediaRouter2.getInstance(context)
    private val bluetoothIdentityResolver = BluetoothIdentityResolver(context)
    private val monitorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val failureEvents = MutableSharedFlow<Throwable>(extraBufferCapacity = 4)

    override val failures: Flow<Throwable> = failureEvents.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    override val routes: Flow<OutputRoute> = merge(observePlatformEvents(), refreshRequests)
        .onStart { emit(Unit) }
        .debounce(RouteSettleMillis)
        .mapLatest { resolveCurrentRoute() }
        .distinctUntilChanged()
        .retryWhen { cause, attempt ->
            failureEvents.emit(cause)
            delay(routeMonitorRetryDelay(attempt))
            true
        }
        .shareRouteEventsIn(monitorScope)

    override suspend fun currentRoute(): OutputRoute = withContext(Dispatchers.Default) {
        resolveCurrentRoute()
    }

    override fun refresh() {
        refreshRequests.tryEmit(Unit)
    }

    private fun observePlatformEvents(): Flow<Unit> = callbackFlow {
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
                // Controller objects are snapshots and are not required to retain reference
                // identity with systemController. Re-resolving is cheap and the flow is debounced.
                trySend(Unit)
            }
        }
        val routeCallback = object : MediaRouter2.RouteCallback() {
            override fun onRoutesUpdated(routes: List<MediaRoute2Info>) {
                trySend(Unit)
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
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ActionA2dpActiveDeviceChanged)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(ActionHeadsetActiveDeviceChanged)
            addAction(ActionLeAudioConnectionStateChanged)
            addAction(ActionLeAudioActiveDeviceChanged)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val audioCallbackRegistered = runCatching {
            audioManager.registerAudioDeviceCallback(audioCallback, handler)
        }.isSuccess
        val controllerCallbackRegistered = runCatching {
            mediaRouter.registerControllerCallback(context.mainExecutor, controllerCallback)
        }.isSuccess
        val routeCallbackRegistered = runCatching {
            mediaRouter.registerRouteCallback(
                context.mainExecutor,
                routeCallback,
                RouteDiscoveryPreference.Builder(emptyList(), false).build(),
            )
        }.isSuccess
        val receiverRegistered = runCatching {
            ContextCompat.registerReceiver(
                context,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )
        }.isSuccess
        if (
            audioCallbackRegistered ||
            controllerCallbackRegistered ||
            routeCallbackRegistered ||
            receiverRegistered
        ) {
            trySend(Unit)
        } else {
            close(IllegalStateException("无法注册任何播放设备监听器"))
        }
        awaitClose {
            if (audioCallbackRegistered) {
                runCatching { audioManager.unregisterAudioDeviceCallback(audioCallback) }
            }
            if (controllerCallbackRegistered) {
                runCatching { mediaRouter.unregisterControllerCallback(controllerCallback) }
            }
            if (routeCallbackRegistered) {
                runCatching { mediaRouter.unregisterRouteCallback(routeCallback) }
            }
            if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        }
    }

    private fun resolveCurrentRoute(): OutputRoute {
        val candidates = buildList {
            val routedDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                runCatching { audioManager.getAudioDevicesForAttributes(MediaAttributes) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            addAll(routedDevices.toOutputRoutes())

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                addAll(
                    runCatching { mediaRouter.systemController.selectedRoutes }
                        .getOrDefault(emptyList())
                        .mapNotNull { route -> runCatching { fromMediaRoute(route) }.getOrNull() },
                )
            }

            addAll(
                audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .toList()
                    .toOutputRoutes(),
            )
        }
        return candidates.preferredOutputRoute() ?: OutputRoute.Speaker
    }

    private fun fromAudioDevice(device: AudioDeviceInfo): OutputRoute? {
        if (!runCatching { device.isSink }.getOrDefault(false)) return null
        val deviceType = runCatching { device.type }.getOrNull() ?: return null
        val type = when (deviceType) {
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
        val displayName = runCatching { device.productName?.toString().orEmpty() }
            .getOrDefault("")
        val reportedAddress = runCatching { device.address }.getOrDefault("")
        val address = if (type.isBluetooth) {
            bluetoothIdentityResolver.resolve(reportedAddress, displayName).orEmpty()
        } else {
            reportedAddress
        }
        return RouteIdentity.create(
            type = type,
            displayName = displayName,
            address = address,
            fallbackIdentity = "$deviceType:${displayName.ifBlank { "unknown" }}",
            legacyFallbackIdentities = if (type.isBluetooth) {
                setOf(
                    "$deviceType:${displayName.ifBlank { "unknown" }}",
                    "${type.name}:${displayName.ifBlank { "unknown" }}",
                    SystemMediaDefaultRouteId,
                )
            } else {
                emptySet()
            },
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
        val displayName = runCatching { route.name.toString() }.getOrDefault("")
        val routeId = runCatching { route.id }.getOrDefault(displayName)
        val address = if (type.isBluetooth) {
            bluetoothIdentityResolver.resolve(
                reportedAddress = extractBluetoothReportedAddress(routeId),
                displayName = displayName,
            ).orEmpty()
        } else {
            ""
        }
        return RouteIdentity.create(
            type = type,
            displayName = displayName,
            address = address,
            fallbackIdentity = routeId,
            legacyFallbackIdentities = if (type.isBluetooth) {
                setOf(
                    routeId,
                    "${type.name}:${displayName.ifBlank { "unknown" }}",
                    SystemMediaDefaultRouteId,
                )
            } else {
                emptySet()
            },
        )
    }

    private fun List<AudioDeviceInfo>.toOutputRoutes(): List<OutputRoute> =
        mapNotNull { device -> runCatching { fromAudioDevice(device) }.getOrNull() }

    private companion object {
        const val RouteSettleMillis = 400L
        // Active-device changes are hidden from the public SDK surface on some supported Android
        // versions. These stable actions are only event hints; route resolution remains public.
        const val ActionA2dpActiveDeviceChanged =
            "android.bluetooth.a2dp.profile.action.ACTIVE_DEVICE_CHANGED"
        const val ActionHeadsetActiveDeviceChanged =
            "android.bluetooth.headset.profile.action.ACTIVE_DEVICE_CHANGED"
        const val ActionLeAudioConnectionStateChanged =
            "android.bluetooth.action.LE_AUDIO_CONNECTION_STATE_CHANGED"
        const val ActionLeAudioActiveDeviceChanged =
            "android.bluetooth.action.LE_AUDIO_ACTIVE_DEVICE_CHANGED"
        const val SystemMediaDefaultRouteId =
            "com.android.server.media/.SystemMediaRoute2Provider:DEFAULT_ROUTE"
        val MediaAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}

internal fun List<OutputRoute>.preferredOutputRoute(): OutputRoute? =
    maxByOrNull { route ->
        val typePriority = when (route.type) {
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
        typePriority * 2 + if (route.identityKind == OutputRouteIdentityKind.PERSISTENT) 1 else 0
    }

internal fun extractBluetoothHardwareAddress(routeId: String): String? =
    extractBluetoothReportedAddress(routeId)
        ?.let(::normalizeBluetoothHardwareAddress)

internal fun extractBluetoothReportedAddress(routeId: String): String? =
    BluetoothAddressInRouteId.find(routeId)?.value

internal fun routeMonitorRetryDelay(attempt: Long): Long =
    (attempt.coerceIn(0L, MaxRouteMonitorRetryAttempt) + 1L) * 1_000L

internal fun Flow<OutputRoute>.shareRouteEventsIn(scope: CoroutineScope): SharedFlow<OutputRoute> =
    shareIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(
            stopTimeoutMillis = 0L,
            replayExpirationMillis = 0L,
        ),
        replay = 1,
    )

private val BluetoothAddressInRouteId =
    Regex("(?i)(?<![0-9a-f])(?:(?:[0-9a-f]{2}:){5}[0-9a-f]{2}|XX:XX:XX:XX:[0-9a-f]{2}:[0-9a-f]{2})(?![0-9a-f])")

private const val MaxRouteMonitorRetryAttempt = 29L
