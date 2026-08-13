package com.weich.daptune.data

import android.database.sqlite.SQLiteConstraintException
import com.weich.daptune.core.eq.BuiltInPresets
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.EqProfile
import com.weich.daptune.core.model.ProfileSource
import com.weich.daptune.core.model.withCustomProfilesFirst
import com.weich.daptune.domain.ProfileRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class ProfileRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
) : ProfileRepository {
    private val builtInsInitialization = Mutex()

    @Volatile
    private var builtInsInitialized = false

    override val profiles: Flow<List<EqProfile>> = profileDao.observeAll().map { entities ->
        entities.map { entity -> entity.toModel() }.withCustomProfilesFirst()
    }

    override suspend fun ensureBuiltIns() {
        if (builtInsInitialized) return
        builtInsInitialization.withLock {
            if (builtInsInitialized) return@withLock

            val now = System.currentTimeMillis()
            val currentBuiltIns = BuiltInPresets.all
            profileDao.upsertAll(
                currentBuiltIns.map { preset ->
                    val existing = profileDao.getById(preset.id)
                    ProfileEntity(
                        id = preset.id,
                        name = preset.name,
                        curveQ4 = CurveBlobCodec.encode(preset.curve),
                        builtIn = true,
                        source = ProfileSource.BUILT_IN.name,
                        sortOrder = preset.sortOrder,
                        createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                    )
                },
            )
            profileDao.deleteObsoleteBuiltIns(currentBuiltIns.map { it.id })
            builtInsInitialized = true
        }
    }

    override suspend fun getProfile(id: String): EqProfile? = profileDao.getById(id)?.toModel()

    override suspend fun saveUserProfile(
        id: String?,
        name: String,
        curve: EqCurve,
        source: ProfileSource,
    ): EqProfile {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "配置名称不能为空" }
        require(cleanName.length <= 40) { "配置名称不能超过 40 个字符" }
        val existing = id?.let { profileDao.getById(it) }
        val actualId = if (existing?.builtIn == true || id == null) UUID.randomUUID().toString() else id
        require(!profileDao.nameExists(cleanName, actualId)) { "已有同名配置" }
        val now = System.currentTimeMillis()
        val entity = ProfileEntity(
            id = actualId,
            name = cleanName,
            curveQ4 = CurveBlobCodec.encode(curve),
            builtIn = false,
            source = source.name,
            sortOrder = 1_000,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        try {
            profileDao.upsert(entity)
        } catch (error: SQLiteConstraintException) {
            throw IllegalArgumentException("已有同名配置", error)
        }
        return entity.toModel()
    }

    override suspend fun deleteProfile(id: String) {
        val entity = profileDao.getById(id) ?: return
        require(!entity.builtIn) { "内置配置不能删除" }
        profileDao.delete(entity)
    }

    private fun ProfileEntity.toModel(): EqProfile = EqProfile(
        id = id,
        name = name,
        curve = CurveBlobCodec.decode(curveQ4),
        isBuiltIn = builtIn,
        source = runCatching { ProfileSource.valueOf(source) }.getOrDefault(ProfileSource.MANUAL),
        createdAtEpochMillis = createdAtEpochMillis,
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
