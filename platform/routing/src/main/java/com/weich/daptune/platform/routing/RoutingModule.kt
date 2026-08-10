package com.weich.daptune.platform.routing

import com.weich.daptune.domain.AudioRouteMonitor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RoutingModule {
    @Binds
    @Singleton
    abstract fun bindAudioRouteMonitor(implementation: SystemAudioRouteMonitor): AudioRouteMonitor
}
