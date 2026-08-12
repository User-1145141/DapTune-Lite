package com.weich.daptune.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ProfileEntity::class,
        KnownDeviceEntity::class,
        DeviceBindingEntity::class,
        AppliedStateEntity::class,
        OperationLogEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class DapTuneDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun deviceDao(): DeviceDao

    abstract fun appliedStateDao(): AppliedStateDao

    abstract fun operationLogDao(): OperationLogDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `operation_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `occurredAtEpochMillis` INTEGER NOT NULL,
                        `action` TEXT NOT NULL,
                        `outcome` TEXT NOT NULL,
                        `routeKey` TEXT,
                        `routeName` TEXT,
                        `profileId` TEXT,
                        `profileName` TEXT,
                        `verification` TEXT,
                        `detail` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_operation_logs_occurredAtEpochMillis` " +
                        "ON `operation_logs` (`occurredAtEpochMillis`)",
                )
            }
        }
    }
}
