package com.weich.daptune.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY builtIn DESC, sortOrder ASC, name COLLATE NOCASE ASC")
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
