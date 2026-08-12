package com.weich.daptune.data

import com.weich.daptune.core.model.OperationLogAction
import com.weich.daptune.core.model.OperationLogEntry
import com.weich.daptune.core.model.OperationLogOutcome
import com.weich.daptune.core.model.VerificationState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationLogRepositoryImplTest {
    @Test
    fun mapsStoredEntriesAndKeepsRepositoryBounded() = runBlocking {
        val stored = OperationLogEntity(
            id = 42L,
            occurredAtEpochMillis = 1234L,
            action = OperationLogAction.ROUTE_CHANGED.name,
            outcome = OperationLogOutcome.SUCCESS.name,
            routeKey = "bluetooth:test",
            routeName = "Test headphones",
            profileId = "custom.test",
            profileName = "Reference",
            verification = VerificationState.VERIFIED.name,
            detail = null,
        )
        val dao = RecordingOperationLogDao(stored)
        val repository = OperationLogRepositoryImpl(dao)

        val mapped = repository.entries.first().single()
        repository.append(mapped.copy(id = 0L))

        assertEquals(42L, mapped.id)
        assertEquals(OperationLogAction.ROUTE_CHANGED, mapped.action)
        assertEquals(OperationLogOutcome.SUCCESS, mapped.outcome)
        assertEquals(VerificationState.VERIFIED, mapped.verification)
        assertEquals(300, dao.pruneLimit)
        assertEquals(0L, dao.inserted?.id)
    }

    private class RecordingOperationLogDao(
        stored: OperationLogEntity,
    ) : OperationLogDao {
        private val values = MutableStateFlow(listOf(stored))
        var inserted: OperationLogEntity? = null
        var pruneLimit: Int? = null

        override fun observeRecent(limit: Int): Flow<List<OperationLogEntity>> = values

        override suspend fun insert(entry: OperationLogEntity) {
            inserted = entry
        }

        override suspend fun prune(maxEntries: Int) {
            pruneLimit = maxEntries
        }

        override suspend fun clear() {
            values.value = emptyList()
        }
    }
}
