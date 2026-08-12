package com.weich.daptune.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY builtIn ASC, sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM profiles " +
            "WHERE name = :name COLLATE NOCASE AND (:excludingId IS NULL OR id != :excludingId))",
    )
    suspend fun nameExists(name: String, excludingId: String?): Boolean

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Upsert
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Query("DELETE FROM profiles WHERE builtIn = 1 AND id NOT IN (:currentIds)")
    suspend fun deleteObsoleteBuiltIns(currentIds: List<String>)

    @Delete
    suspend fun delete(profile: ProfileEntity)
}

@Dao
interface DeviceDao {
    @Query("SELECT * FROM known_devices ORDER BY lastSeenAtEpochMillis DESC")
    fun observeKnownDevices(): Flow<List<KnownDeviceEntity>>

    @Query("SELECT * FROM device_bindings ORDER BY routeKey ASC")
    fun observeBindings(): Flow<List<DeviceBindingEntity>>

    @Query("SELECT profileId FROM device_bindings WHERE routeKey = :routeKey LIMIT 1")
    suspend fun getBoundProfileId(routeKey: String): String?

    @Upsert
    suspend fun upsertDevice(device: KnownDeviceEntity)

    @Upsert
    suspend fun upsertBinding(binding: DeviceBindingEntity)

    @Query("DELETE FROM device_bindings WHERE routeKey = :routeKey")
    suspend fun deleteBinding(routeKey: String)

    @Query("DELETE FROM known_devices WHERE routeKey = :routeKey")
    suspend fun deleteKnownDevice(routeKey: String)

    @Query("DELETE FROM applied_state WHERE routeKey = :routeKey")
    suspend fun deleteAppliedState(routeKey: String)

    @Transaction
    suspend fun forgetDevice(routeKey: String) {
        deleteBinding(routeKey)
        deleteAppliedState(routeKey)
        deleteKnownDevice(routeKey)
    }
}

@Dao
interface AppliedStateDao {
    @Query("SELECT * FROM applied_state WHERE singletonId = 0 LIMIT 1")
    fun observe(): Flow<AppliedStateEntity?>

    @Query("SELECT * FROM applied_state WHERE singletonId = 0 LIMIT 1")
    suspend fun get(): AppliedStateEntity?

    @Upsert
    suspend fun upsert(state: AppliedStateEntity)
}

@Dao
interface OperationLogDao {
    @Query(
        "SELECT * FROM operation_logs " +
            "ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    fun observeRecent(limit: Int): Flow<List<OperationLogEntity>>

    @Insert
    suspend fun insert(entry: OperationLogEntity)

    @Query(
        "DELETE FROM operation_logs WHERE id NOT IN (" +
            "SELECT id FROM operation_logs " +
            "ORDER BY occurredAtEpochMillis DESC, id DESC LIMIT :maxEntries)",
    )
    suspend fun prune(maxEntries: Int)

    @Query("DELETE FROM operation_logs")
    suspend fun clear()

    @Transaction
    suspend fun insertBounded(entry: OperationLogEntity, maxEntries: Int) {
        insert(entry)
        prune(maxEntries)
    }
}
