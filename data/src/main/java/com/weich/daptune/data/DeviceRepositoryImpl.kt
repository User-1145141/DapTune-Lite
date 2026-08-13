package com.weich.daptune.data

import com.weich.daptune.core.model.AppliedSnapshot
import com.weich.daptune.core.model.DeviceBinding
import com.weich.daptune.core.model.KnownOutputDevice
import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteIdentityKind
import com.weich.daptune.core.model.OutputRouteType
import com.weich.daptune.core.model.VerificationState
import com.weich.daptune.domain.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DeviceRepositoryImpl @Inject constructor(
    private val deviceDao: DeviceDao,
    private val appliedStateDao: AppliedStateDao,
) : DeviceRepository {
    override val knownDevices: Flow<List<KnownOutputDevice>> = deviceDao.observeKnownDevices().map { entities ->
        entities.map { entity ->
            KnownOutputDevice(
                route = OutputRoute(
                    key = entity.routeKey,
                    displayName = entity.displayName,
                    type = runCatching { OutputRouteType.valueOf(entity.routeType) }
                        .getOrDefault(OutputRouteType.UNKNOWN),
                    rawAddressPresent = entity.rawAddressPresent,
                ),
                lastSeenAtEpochMillis = entity.lastSeenAtEpochMillis,
            )
        }
    }

    override val bindings: Flow<List<DeviceBinding>> = deviceDao.observeBindings().map { entities ->
        entities.map { DeviceBinding(it.routeKey, it.profileId) }
    }

    override val appliedSnapshot: Flow<AppliedSnapshot?> = appliedStateDao.observe().map { entity ->
        entity?.let {
            AppliedSnapshot(
                routeKey = it.routeKey,
                profileId = it.profileId,
                curveHash = it.curveHash,
                appliedAtEpochMillis = it.appliedAtEpochMillis,
                verification = runCatching { VerificationState.valueOf(it.verification) }
                    .getOrDefault(VerificationState.STALE),
            )
        }
    }

    override suspend fun rememberRoute(route: OutputRoute) {
        if (route.identityKind != OutputRouteIdentityKind.PERSISTENT) return
        deviceDao.rememberPersistentDevice(
            device = KnownDeviceEntity(
                routeKey = route.key,
                displayName = route.displayName,
                routeType = route.type.name,
                rawAddressPresent = route.rawAddressPresent,
                lastSeenAtEpochMillis = System.currentTimeMillis(),
            ),
            legacyRouteKeys = route.legacyKeys.toList(),
        )
    }

    override suspend fun forgetRoute(routeKey: String) {
        deviceDao.forgetDevice(routeKey)
    }

    override suspend fun bind(routeKey: String, profileId: String?) {
        if (routeKey.startsWith(TransientRoutePrefix)) return
        if (profileId == null) {
            deviceDao.deleteBinding(routeKey)
        } else {
            deviceDao.upsertBinding(DeviceBindingEntity(routeKey, profileId))
        }
    }

    override suspend fun getBoundProfileId(routeKey: String): String? =
        deviceDao.getBoundProfileId(routeKey)

    override suspend fun updateAppliedSnapshot(snapshot: AppliedSnapshot) {
        appliedStateDao.upsert(
            AppliedStateEntity(
                routeKey = snapshot.routeKey,
                profileId = snapshot.profileId,
                curveHash = snapshot.curveHash,
                appliedAtEpochMillis = snapshot.appliedAtEpochMillis,
                verification = snapshot.verification.name,
            ),
        )
    }

    override suspend fun markAppliedStateStale() {
        val existing = appliedStateDao.get() ?: return
        appliedStateDao.upsert(existing.copy(verification = VerificationState.STALE.name))
    }

    private companion object {
        const val TransientRoutePrefix = "transient:"
    }
}
