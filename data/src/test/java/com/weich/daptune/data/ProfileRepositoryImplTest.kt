package com.weich.daptune.data

import com.weich.daptune.core.eq.BuiltInPresets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRepositoryImplTest {
    @Test
    fun concurrentBuiltInInitializationWritesExactlyOnce() = runBlocking {
        val dao = RecordingProfileDao()
        val repository = ProfileRepositoryImpl(dao)

        coroutineScope {
            List(16) {
                async(Dispatchers.Default) { repository.ensureBuiltIns() }
            }.awaitAll()
        }
        repository.ensureBuiltIns()

        assertEquals(1, dao.upsertAllAttempts.get())
        assertEquals(1, dao.deleteObsoleteAttempts.get())
        assertEquals(BuiltInPresets.all.map { it.id }.toSet(), dao.profiles.keys)
    }

    @Test
    fun failedBuiltInInitializationCanBeRetried() = runBlocking {
        val dao = RecordingProfileDao(failFirstUpsert = true)
        val repository = ProfileRepositoryImpl(dao)

        val failed = runCatching { repository.ensureBuiltIns() }
        repository.ensureBuiltIns()

        assertTrue(failed.isFailure)
        assertEquals(2, dao.upsertAllAttempts.get())
        assertEquals(1, dao.deleteObsoleteAttempts.get())
    }

    private class RecordingProfileDao(
        failFirstUpsert: Boolean = false,
    ) : ProfileDao {
        val profiles = ConcurrentHashMap<String, ProfileEntity>()
        val upsertAllAttempts = AtomicInteger(0)
        val deleteObsoleteAttempts = AtomicInteger(0)
        private val failNextUpsert = AtomicBoolean(failFirstUpsert)

        override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(emptyList())

        override suspend fun getById(id: String): ProfileEntity? = profiles[id]

        override suspend fun nameExists(name: String, excludingId: String?): Boolean =
            profiles.values.any { it.name.equals(name, ignoreCase = true) && it.id != excludingId }

        override suspend fun upsert(profile: ProfileEntity) {
            profiles[profile.id] = profile
        }

        override suspend fun upsertAll(profiles: List<ProfileEntity>) {
            upsertAllAttempts.incrementAndGet()
            if (failNextUpsert.compareAndSet(true, false)) error("injected failure")
            profiles.forEach { profile -> this.profiles[profile.id] = profile }
        }

        override suspend fun deleteObsoleteBuiltIns(currentIds: List<String>) {
            deleteObsoleteAttempts.incrementAndGet()
            profiles.entries.removeIf { (id, profile) -> profile.builtIn && id !in currentIds }
        }

        override suspend fun delete(profile: ProfileEntity) {
            profiles.remove(profile.id)
        }
    }
}
