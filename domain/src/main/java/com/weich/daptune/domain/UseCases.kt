package com.weich.daptune.domain

import com.weich.daptune.core.eq.CurveFileCodec
import com.weich.daptune.core.eq.ImportedCurve
import com.weich.daptune.core.eq.OverflowMode
import com.weich.daptune.core.eq.EqTransforms
import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.ProfileSource
import com.weich.daptune.core.model.VerificationState
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ApplyProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val dapGateway: DapGateway,
) {
    suspend operator fun invoke(profileId: String, route: OutputRoute): DapApplyResult {
        val profile = profileRepository.getProfile(profileId)
            ?: return DapApplyResult.Failure(
                reason = com.weich.daptune.core.model.DapFailureReason.INVALID_STATE,
                detail = "配置不存在",
            )
        val result = dapGateway.applyCurve(profile.curve)
        when (result) {
            is DapApplyResult.Success -> deviceRepository.updateAppliedSnapshot(
                AppliedSnapshot(
                    routeKey = route.key,
                    profileId = profile.id,
                    curveHash = profile.curve.stableHash(),
                    appliedAtEpochMillis = System.currentTimeMillis(),
                    verification = if (result.receipt.verified) {
                        VerificationState.VERIFIED
                    } else {
                        VerificationState.STALE
                    },
                ),
            )
            is DapApplyResult.Failure -> deviceRepository.markAppliedStateStale()
        }
        return result
    }
}

class ApplyCurveUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val dapGateway: DapGateway,
) {
    suspend operator fun invoke(
        profileId: String,
        curve: EqCurve,
        route: OutputRoute,
    ): DapApplyResult {
        val result = dapGateway.applyCurve(curve)
        when (result) {
            is DapApplyResult.Success -> deviceRepository.updateAppliedSnapshot(
                AppliedSnapshot(
                    routeKey = route.key,
                    profileId = profileId,
                    curveHash = curve.stableHash(),
                    appliedAtEpochMillis = System.currentTimeMillis(),
                    verification = VerificationState.VERIFIED,
                ),
            )
            is DapApplyResult.Failure -> deviceRepository.markAppliedStateStale()
        }
        return result
    }
}

class ResolveProfileForRouteUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val deviceRepository: DeviceRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(route: OutputRoute): EqProfile? {
        val exact = deviceRepository.getBoundProfileId(route.key)
        val typeFallback = deviceRepository.getBoundProfileId(OutputRoute.typeFallback(route.type))
        val defaultId = settingsRepository.settings.first().defaultProfileId
        return profileRepository.getProfile(exact ?: typeFallback ?: defaultId)
            ?: profileRepository.getProfile("builtin.flat")
    }
}

class SaveProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        existingId: String?,
        name: String,
        curve: EqCurve,
        source: ProfileSource,
    ): EqProfile {
        val cleanName = name.trim()
        val resolvedName = if (existingId == null) {
            ProfileNames.uniqueCopyName(
                preferredName = cleanName,
                existingNames = profileRepository.profiles.first().map(EqProfile::name),
            )
        } else {
            cleanName
        }
        return profileRepository.saveUserProfile(
            id = existingId,
            name = resolvedName,
            curve = curve,
            source = source,
        )
    }
}

class ImportCurveUseCase @Inject constructor() {
    fun parse(text: String, fileName: String): ImportedCurve = CurveFileCodec.import(text, fileName)

    fun convert(imported: ImportedCurve, overflowMode: OverflowMode): EqCurve =
        EqTransforms.quantize(imported.gainsDb, overflowMode).curve
}

class DuplicateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(profile: EqProfile, newName: String): EqProfile {
        val candidate = ProfileNames.uniqueCopyName(
            preferredName = newName,
            existingNames = profileRepository.profiles.first().map(EqProfile::name),
        )
        return profileRepository.saveUserProfile(
            id = UUID.randomUUID().toString(),
            name = candidate,
            curve = profile.curve,
            source = ProfileSource.MANUAL,
        )
    }
}
