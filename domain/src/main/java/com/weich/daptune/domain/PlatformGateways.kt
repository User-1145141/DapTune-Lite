package com.weich.daptune.domain

import com.weich.daptune.core.model.DapApplyResult
import com.weich.daptune.core.model.DapCapability
import com.weich.daptune.core.model.EqCurve
import com.weich.daptune.core.model.OutputRoute
import kotlinx.coroutines.flow.Flow

interface DapGateway {
    suspend fun inspect(): DapCapability

    suspend fun readAllProfileCurves(): Result<List<EqCurve>>

    suspend fun applyCurve(curve: EqCurve): DapApplyResult
}

interface AudioRouteMonitor {
    val routes: Flow<OutputRoute>
    val failures: Flow<Throwable>

    suspend fun currentRoute(): OutputRoute

    /** Requests an immediate snapshot without waiting for a platform route event. */
    fun refresh() = Unit
}
