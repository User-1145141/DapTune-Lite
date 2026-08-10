package com.weich.daptune.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index(value = ["name"], unique = true)],
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val curveQ4: ByteArray,
    val builtIn: Boolean,
    val source: String,
    val sortOrder: Int,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            other is ProfileEntity &&
            id == other.id &&
            name == other.name &&
            curveQ4.contentEquals(other.curveQ4) &&
            builtIn == other.builtIn &&
            source == other.source &&
            sortOrder == other.sortOrder &&
            createdAtEpochMillis == other.createdAtEpochMillis &&
            updatedAtEpochMillis == other.updatedAtEpochMillis

    override fun hashCode(): Int = 31 * id.hashCode() + curveQ4.contentHashCode()
}

@Entity(tableName = "known_devices")
data class KnownDeviceEntity(
    @PrimaryKey val routeKey: String,
    val displayName: String,
    val routeType: String,
    val rawAddressPresent: Boolean,
    val lastSeenAtEpochMillis: Long,
)

@Entity(
    tableName = "device_bindings",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("profileId")],
)
data class DeviceBindingEntity(
    @PrimaryKey val routeKey: String,
    val profileId: String,
)

@Entity(tableName = "applied_state")
data class AppliedStateEntity(
    @PrimaryKey val singletonId: Int = 0,
    val routeKey: String,
    val profileId: String,
    val curveHash: Int,
    val appliedAtEpochMillis: Long,
    val verification: String,
)
