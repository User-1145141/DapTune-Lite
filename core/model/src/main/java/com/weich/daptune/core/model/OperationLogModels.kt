package com.weich.daptune.core.model

data class OperationLogEntry(
    val id: Long = 0L,
    val occurredAtEpochMillis: Long,
    val action: OperationLogAction,
    val outcome: OperationLogOutcome,
    val routeKey: String? = null,
    val routeName: String? = null,
    val profileId: String? = null,
    val profileName: String? = null,
    val verification: VerificationState? = null,
    val detail: String? = null,
)

enum class OperationLogAction {
    UNKNOWN,
    AUTOMATION_ENABLED,
    AUTOMATION_DISABLED,
    START_AT_BOOT_ENABLED,
    START_AT_BOOT_DISABLED,
    AUTOMATION_STARTED,
    AUTOMATION_RECOVERED,
    AUTOMATION_START_FAILED,
    ROUTE_CHANGED,
    DOLBY_RESTORED,
    AUTOMATION_REFRESHED,
    PROFILE_SELECTED,
    DEFAULT_RULE_CHANGED,
    DEVICE_RULE_CHANGED,
    CURVE_APPLIED,
    DEVICE_FORGOTTEN,
}

enum class OperationLogOutcome {
    INFO,
    SUCCESS,
    FAILURE,
}

enum class ProfileApplySource {
    AUTOMATION_START,
    AUTOMATION_RECOVERY,
    ROUTE_CHANGE,
    DOLBY_RESTORED,
    AUTOMATION_REFRESH,
    MANUAL_SELECTION,
    DEFAULT_RULE_CHANGE,
    DEVICE_RULE_CHANGE,
}
