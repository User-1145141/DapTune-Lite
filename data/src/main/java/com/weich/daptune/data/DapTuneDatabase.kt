package com.weich.daptune.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProfileEntity::class,
        KnownDeviceEntity::class,
        DeviceBindingEntity::class,
        AppliedStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class DapTuneDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun deviceDao(): DeviceDao

    abstract fun appliedStateDao(): AppliedStateDao
}
