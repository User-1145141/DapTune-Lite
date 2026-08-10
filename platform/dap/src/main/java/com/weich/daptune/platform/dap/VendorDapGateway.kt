package com.weich.daptune.platform.dap

import android.media.audiofx.AudioEffect
import com.weich.daptune.core.model.DapApplyReceipt
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.DapCapability
import com.weich.daptune.core.model.DapFailureReason
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.domain.DapGateway
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass

@Singleton
class VendorDapGateway @Inject constructor() : DapGateway {
    private val transactionMutex = Mutex()

    override suspend fun inspect(): DapCapability = withContext(Dispatchers.IO) {
        val descriptorFound = runCatching {
            AudioEffect.queryEffects()?.any { it.uuid == DapImplementationUuid } == true
        }.getOrDefault(false)
        if (!descriptorFound) return@withContext DapCapability(
            descriptorFound = false,
            hasControl = false,
            effectEnabled = false,
            dapEnabled = false,
            profileCount = 0,
            currentProfile = -1,
            detail = "未找到兼容的 Dolby DAP",
        )
        var effect: AudioEffect? = null
        try {
            val api = bridge()
            effect = api.open()
            DapCapability(
                descriptorFound = true,
                hasControl = effect.hasControl(),
                effectEnabled = effect.enabled,
                dapEnabled = api.queryInt(effect, DapProtocol.commandGetDapOn) > 0,
                profileCount = api.queryInt(effect, DapProtocol.commandGetProfileCount),
                currentProfile = api.queryInt(effect, DapProtocol.commandGetCurrentProfile),
            )
        } catch (error: Throwable) {
            DapCapability(
                descriptorFound = true,
                hasControl = false,
                effectEnabled = false,
                dapEnabled = false,
                profileCount = 0,
                currentProfile = -1,
                detail = describe(unwrap(error)),
            )
        } finally {
            effect.safeRelease()
        }
    }

    override suspend fun readAllProfileCurves(): Result<List<EqCurve>> =
        transactionMutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    var effect: AudioEffect? = null
                    try {
                        val api = bridge()
                        effect = api.open()
                        require(effect.hasControl()) { "DAP 控制权被其他客户端占用" }
                        val profileCount = api.queryInt(effect, DapProtocol.commandGetProfileCount)
                        require(profileCount in 1..MaxProfileCount) { "无效的 Dolby profile 数量：$profileCount" }
                        List(profileCount) { profile -> EqCurve.ofQ4(api.readProfileCurve(effect, profile)) }
                    } finally {
                        effect.safeRelease()
                    }
                }
            }
        }

    override suspend fun applyCurve(curve: EqCurve): DapApplyResult =
        transactionMutex.withLock {
            withContext(Dispatchers.IO) { applyCurveBlocking(curve) }
        }

    private fun applyCurveBlocking(curve: EqCurve): DapApplyResult {
        var effect: AudioEffect? = null
        var originals: List<IntArray> = emptyList()
        var writesStarted = false
        return try {
            if (AudioEffect.queryEffects()?.none { it.uuid == DapImplementationUuid } != false) {
                return DapApplyResult.Failure(DapFailureReason.UNSUPPORTED, "未找到兼容的 Dolby DAP")
            }
            val api = bridge()
            effect = api.open()
            if (!effect.hasControl()) {
                return DapApplyResult.Failure(DapFailureReason.NO_CONTROL, "DAP 控制权被其他客户端占用")
            }
            if (!effect.enabled || api.queryInt(effect, DapProtocol.commandGetDapOn) <= 0) {
                return DapApplyResult.Failure(DapFailureReason.DOLBY_DISABLED, "杜比全景声已关闭")
            }
            val profileCount = api.queryInt(effect, DapProtocol.commandGetProfileCount)
            if (profileCount !in 1..MaxProfileCount) {
                return DapApplyResult.Failure(
                    DapFailureReason.INVALID_STATE,
                    "无效的 Dolby profile 数量：$profileCount",
                )
            }
            originals = List(profileCount) { profile -> api.readProfileCurve(effect, profile) }
            val target = curve.toQ4Array()
            writesStarted = true
            repeat(profileCount) { profile -> api.writeProfileCurve(effect, profile, target) }

            val mismatch = (0 until profileCount).firstOrNull { profile ->
                !api.readProfileCurve(effect, profile).contentEquals(target)
            }
            if (mismatch != null) {
                rollback(api, effect, originals)
                return DapApplyResult.Failure(
                    DapFailureReason.VERIFICATION_FAILED,
                    "Profile $mismatch 回读不一致，已恢复原设置",
                )
            }
            api.saveSettings(effect)
            val currentProfile = api.queryInt(effect, DapProtocol.commandGetCurrentProfile)
            DapApplyResult.Success(
                DapApplyReceipt(
                    profileCount = profileCount,
                    currentProfile = currentProfile,
                    verified = true,
                    curveHash = curve.stableHash(),
                ),
            )
        } catch (error: Throwable) {
            val cause = unwrap(error)
            if (writesStarted && effect != null && originals.isNotEmpty()) {
                runCatching { rollback(bridge(), effect, originals) }
            }
            DapApplyResult.Failure(
                reason = failureReason(cause),
                detail = describe(cause),
                cause = cause,
            )
        } finally {
            effect.safeRelease()
        }
    }

    private fun rollback(api: Bridge, effect: AudioEffect, originals: List<IntArray>) {
        originals.forEachIndexed { profile, curve -> api.writeProfileCurve(effect, profile, curve) }
        api.saveSettings(effect)
    }

    private fun bridge(): Bridge {
        BridgeInstance?.let { return it }
        synchronized(BridgeLock) {
            BridgeInstance?.let { return it }
            check(HiddenApiBypass.addHiddenApiExemptions("Landroid/media/audiofx/AudioEffect")) {
                "无法初始化系统音效接口"
            }
            return Bridge().also { BridgeInstance = it }
        }
    }

    private class Bridge {
        private val constructor: Constructor<*> = AudioEffect::class.java.getDeclaredConstructor(
            UUID::class.java,
            UUID::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        private val setParameter: Method = AudioEffect::class.java.getMethod(
            "setParameter",
            Int::class.javaPrimitiveType,
            ByteArray::class.java,
        )
        private val getParameter: Method = AudioEffect::class.java.getMethod(
            "getParameter",
            Int::class.javaPrimitiveType,
            ByteArray::class.java,
        )

        fun open(): AudioEffect = constructor.newInstance(
            DapTypeUuid,
            DapImplementationUuid,
            PriorityNormal,
            GlobalMixSession,
        ) as AudioEffect

        fun queryInt(effect: AudioEffect, command: Int): Int {
            val response = DapProtocol.createQueryBuffer(command)
            val status = getParameter.invoke(effect, DapProtocol.queryKey(command), response) as Int
            checkStatus(status, "读取 0x${command.toString(16)}")
            return DapProtocol.readFirstInt(response)
        }

        fun readProfileCurve(effect: AudioEffect, profile: Int): IntArray {
            val response = DapProtocol.createCurveReadBuffer()
            val key = DapProtocol.profileParameterReadKey(
                profile,
                DapProtocol.parameterGraphicEqualizerBandGains,
            )
            val status = getParameter.invoke(effect, key, response) as Int
            checkStatus(status, "读取 profile $profile")
            return DapProtocol.readCurve(response)
        }

        fun writeProfileCurve(effect: AudioEffect, profile: Int, gainsQ4: IntArray) {
            val payload = DapProtocol.createProfileCurvePayload(profile, gainsQ4)
            val status = setParameter.invoke(effect, DapProtocol.effectParameterCommand, payload) as Int
            checkStatus(status, "写入 profile $profile")
        }

        fun saveSettings(effect: AudioEffect) {
            val status = setParameter.invoke(
                effect,
                DapProtocol.effectParameterCommand,
                DapProtocol.createSavePayload(),
            ) as Int
            checkStatus(status, "保存 Dolby 设置")
        }

        private fun checkStatus(status: Int, operation: String) {
            if (status >= 0) return
            when (status) {
                AudioEffect.ERROR_BAD_VALUE -> throw IllegalArgumentException("$operation：参数无效")
                AudioEffect.ERROR_INVALID_OPERATION -> throw UnsupportedOperationException("$operation：操作无效")
                AudioEffect.ERROR_NO_INIT -> throw IllegalStateException("$operation：音效未初始化")
                AudioEffect.ERROR_DEAD_OBJECT -> throw AudioServiceDiedException("$operation：音频服务已重启")
                else -> throw IllegalStateException("$operation：错误 $status")
            }
        }
    }

    private class AudioServiceDiedException(message: String) : IllegalStateException(message)

    private companion object {
        val DapTypeUuid: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
        val DapImplementationUuid: UUID = UUID.fromString("9d4921da-8225-4f29-aefa-39537a04bcaa")
        const val GlobalMixSession = 0
        const val PriorityNormal = 0
        const val MaxProfileCount = 32
        val BridgeLock = Any()

        @Volatile
        var BridgeInstance: Bridge? = null

        fun AudioEffect?.safeRelease() {
            if (this == null) return
            runCatching { release() }
        }

        fun unwrap(error: Throwable): Throwable {
            var current = error
            while ((current is InvocationTargetException || current.cause != null) &&
                current.cause != null && current.cause !== current
            ) {
                current = current.cause!!
            }
            return current
        }

        fun describe(error: Throwable): String = buildString {
            append(error::class.simpleName ?: "错误")
            error.message?.takeIf(String::isNotBlank)?.let { append("：").append(it) }
        }

        fun failureReason(error: Throwable): DapFailureReason = when (error) {
            is AudioServiceDiedException -> DapFailureReason.AUDIO_SERVICE_DIED
            is UnsupportedOperationException -> DapFailureReason.WRITE_FAILED
            is IllegalArgumentException -> DapFailureReason.INVALID_STATE
            is IllegalStateException -> DapFailureReason.WRITE_FAILED
            else -> DapFailureReason.UNKNOWN
        }
    }
}
