package com.weich.daptune.data

import android.content.Context
import androidx.room.Room
import com.weich.daptune.domain.AutoEqRepository
import com.weich.daptune.domain.DeviceRepository
import com.weich.daptune.domain.OperationLogRepository
import com.weich.daptune.domain.ProfileRepository
import com.weich.daptune.domain.SettingsRepository
import com.weich.daptune.domain.UpdateRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindings {
    @Binds
    @Singleton
    abstract fun bindAutoEqRepository(implementation: AutoEqRepositoryImpl): AutoEqRepository

    @Binds
    @Singleton
    abstract fun bindProfileRepository(implementation: ProfileRepositoryImpl): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(implementation: DeviceRepositoryImpl): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(implementation: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindUpdateRepository(implementation: UpdateRepositoryImpl): UpdateRepository

    @Binds
    @Singleton
    abstract fun bindOperationLogRepository(
        implementation: OperationLogRepositoryImpl,
    ): OperationLogRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DapTuneDatabase =
        Room.databaseBuilder(context, DapTuneDatabase::class.java, "daptune.db")
            .addMigrations(DapTuneDatabase.Migration1To2)
            .build()

    @Provides
    fun provideProfileDao(database: DapTuneDatabase): ProfileDao = database.profileDao()

    @Provides
    fun provideDeviceDao(database: DapTuneDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideAppliedStateDao(database: DapTuneDatabase): AppliedStateDao = database.appliedStateDao()

    @Provides
    fun provideOperationLogDao(database: DapTuneDatabase): OperationLogDao = database.operationLogDao()
}
