package com.weich.daptune.platform.dap

import com.weich.daptune.domain.DapGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DapModule {
    @Binds
    @Singleton
    abstract fun bindDapGateway(implementation: VendorDapGateway): DapGateway
}
