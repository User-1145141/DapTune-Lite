package com.weich.daptune.feature.automation

import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.ProfileApplySource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.isActive

/**
 * Owns exactly one cold platform-route subscription at a time.
 *
 * Route production is conflated away from profile application. A slow Dolby or database call can
 * delay applying the newest route, but it cannot unregister the platform callbacks while it runs.
 * Unexpected upstream completion is treated as a failure and recreates the same single session.
 */
internal class AutomationRouteSession(
    private val routes: Flow<OutputRoute>,
    private val prepare: suspend () -> Unit,
    private val processRoute: suspend (OutputRoute, Boolean, ProfileApplySource) -> Unit,
    private val onFailure: (Throwable) -> Unit,
    private val waitBeforeRetry: suspend (Long) -> Unit = { attempt ->
        delay(automationMonitorRetryDelay(attempt))
    },
) {
    suspend fun run(initialSource: ProfileApplySource) {
        var retryAttempt = 0L
        var firstRouteSource = initialSource
        while (currentCoroutineContext().isActive) {
            try {
                var firstRoute = true
                routes
                    .buffer(Channel.CONFLATED)
                    .collect { route ->
                        // The first shared-route value proves that the underlying platform
                        // callbacks are active before database or DAP work can suspend here.
                        if (firstRoute) prepare()
                        processRoute(
                            route,
                            firstRoute,
                            if (firstRoute) firstRouteSource else ProfileApplySource.ROUTE_CHANGE,
                        )
                        firstRoute = false
                        retryAttempt = 0L
                    }
                error("播放设备监听意外结束")
            } catch (timeout: TimeoutCancellationException) {
                // A startup timeout belongs to the monitored session, not to the parent service.
                // Treat it like any other recoverable monitor failure so the platform callbacks
                // are registered again instead of leaving a foreground service without a listener.
                onFailure(timeout)
                waitBeforeRetry(retryAttempt)
                retryAttempt = (retryAttempt + 1L).coerceAtMost(MaxMonitorRetryAttempt)
                firstRouteSource = ProfileApplySource.AUTOMATION_RECOVERY
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                onFailure(error)
                waitBeforeRetry(retryAttempt)
                retryAttempt = (retryAttempt + 1L).coerceAtMost(MaxMonitorRetryAttempt)
                firstRouteSource = ProfileApplySource.AUTOMATION_RECOVERY
            }
        }
    }
}

internal fun automationMonitorRetryDelay(attempt: Long): Long =
    (attempt.coerceIn(0L, MaxMonitorRetryAttempt) + 1L) * 1_000L

private const val MaxMonitorRetryAttempt = 29L
