package com.weich.daptune.core.model

data class DapCapability(
    val descriptorFound: Boolean,
    val hasControl: Boolean,
    val effectEnabled: Boolean,
    val dapEnabled: Boolean,
    val profileCount: Int,
    val currentProfile: Int,
    val detail: String? = null,
) {
    val isReady: Boolean
        get() = descriptorFound && hasControl && effectEnabled && dapEnabled && profileCount > 0
}

data class DapApplyReceipt(
    val profileCount: Int,
    val currentProfile: Int,
    val verified: Boolean,
    val curveHash: Int,
)

sealed interface DapApplyResult {
    data class Success(val receipt: DapApplyReceipt) : DapApplyResult

    data class Failure(
        val reason: DapFailureReason,
        val detail: String,
        val cause: Throwable? = null,
    ) : DapApplyResult
}

enum class DapFailureReason {
    UNSUPPORTED,
    NO_CONTROL,
    DOLBY_DISABLED,
    INVALID_STATE,
    WRITE_FAILED,
    VERIFICATION_FAILED,
    AUDIO_SERVICE_DIED,
    UNKNOWN,
}
