package com.weich.daptune.data

import com.weich.daptune.core.model.OperationLogAction
import com.weich.daptune.core.model.OperationLogEntry
import com.weich.daptune.core.model.OperationLogOutcome
import com.weich.daptune.core.model.VerificationState
import com.weich.daptune.domain.OperationLogRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class OperationLogRepositoryImpl @Inject constructor(
    private val dao: OperationLogDao,
) : OperationLogRepository {
    override val entries: Flow<List<OperationLogEntry>> =
        dao.observeRecent(StoredEntryLimit).map { entities -> entities.map(OperationLogEntity::toModel) }

    override suspend fun append(entry: OperationLogEntry) {
        dao.insertBounded(entry.toEntity(), StoredEntryLimit)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private companion object {
        const val StoredEntryLimit = 300
    }
}

private fun OperationLogEntity.toModel() = OperationLogEntry(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    action = enumValueOrDefault(action, OperationLogAction.UNKNOWN),
    outcome = enumValueOrDefault(outcome, OperationLogOutcome.INFO),
    routeKey = routeKey,
    routeName = routeName,
    profileId = profileId,
    profileName = profileName,
    verification = verification?.let {
        enumValueOrDefault(it, VerificationState.STALE)
    },
    detail = detail,
)

private fun OperationLogEntry.toEntity() = OperationLogEntity(
    id = id,
    occurredAtEpochMillis = occurredAtEpochMillis,
    action = action.name,
    outcome = outcome.name,
    routeKey = routeKey,
    routeName = routeName,
    profileId = profileId,
    profileName = profileName,
    verification = verification?.name,
    detail = detail,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, fallback: T): T =
    runCatching { enumValueOf<T>(value) }.getOrDefault(fallback)
