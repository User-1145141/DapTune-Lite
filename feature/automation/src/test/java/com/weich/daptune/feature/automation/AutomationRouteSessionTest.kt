package com.weich.daptune.feature.automation

import com.weich.daptune.core.model.OutputRoute
import com.weich.daptune.core.model.OutputRouteType
import com.weich.daptune.core.model.ProfileApplySource
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRouteSessionTest {
    @Test
    fun firstRouteIsForcedOnlyAfterThePlatformFlowIsCollected() = runBlocking {
        val platformRegistered = AtomicBoolean(false)
        val preparationStarted = AtomicBoolean(false)
        val routeProcessed = CompletableDeferred<Unit>()
        val job = launch {
            AutomationRouteSession(
                routes = flow {
                    platformRegistered.set(true)
                    emit(OutputRoute.Speaker)
                    awaitCancellation()
                },
                prepare = {
                    assertTrue(platformRegistered.get())
                    preparationStarted.set(true)
                },
                processRoute = { route, force, source ->
                    assertTrue(platformRegistered.get())
                    assertTrue(preparationStarted.get())
                    assertEquals(OutputRoute.Speaker, route)
                    assertTrue(force)
                    assertEquals(ProfileApplySource.AUTOMATION_START, source)
                    routeProcessed.complete(Unit)
                },
                onFailure = { throw AssertionError(it) },
                waitBeforeRetry = {},
            ).run(ProfileApplySource.AUTOMATION_START)
        }

        routeProcessed.await()
        job.cancelAndJoin()
    }

    @Test
    fun unexpectedCompletionCreatesOneFreshRecoverySession() = runBlocking {
        var subscriptions = 0
        val observations = mutableListOf<Pair<Boolean, ProfileApplySource>>()
        val secondRouteStarted = CompletableDeferred<Unit>()
        val job = launch {
            AutomationRouteSession(
                routes = flow {
                    subscriptions += 1
                    emit(OutputRoute.Speaker)
                },
                prepare = {},
                processRoute = { _, force, source ->
                    observations += force to source
                    if (observations.size == 2) {
                        secondRouteStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
                onFailure = {},
                waitBeforeRetry = {},
            ).run(ProfileApplySource.AUTOMATION_START)
        }

        secondRouteStarted.await()
        job.cancelAndJoin()

        assertEquals(2, subscriptions)
        assertEquals(
            listOf(
                true to ProfileApplySource.AUTOMATION_START,
                true to ProfileApplySource.AUTOMATION_RECOVERY,
            ),
            observations,
        )
    }

    @Test
    fun preparationTimeoutRestartsThePlatformSubscription() = runBlocking {
        var subscriptions = 0
        val recovered = CompletableDeferred<ProfileApplySource>()
        val job = launch {
            AutomationRouteSession(
                routes = flow {
                    subscriptions += 1
                    emit(OutputRoute.Speaker)
                    awaitCancellation()
                },
                prepare = {
                    if (subscriptions == 1) {
                        withTimeout(1L) { awaitCancellation() }
                    }
                },
                processRoute = { _, _, source -> recovered.complete(source) },
                onFailure = {},
                waitBeforeRetry = {},
            ).run(ProfileApplySource.AUTOMATION_RECOVERY)
        }

        assertEquals(ProfileApplySource.AUTOMATION_RECOVERY, recovered.await())
        job.cancelAndJoin()

        assertEquals(2, subscriptions)
    }

    @Test
    fun slowRouteApplicationDoesNotCancelThePlatformSubscription() = runBlocking {
        val platformClosed = AtomicBoolean(false)
        val routeProcessing = CompletableDeferred<Unit>()
        val releaseProcessing = CompletableDeferred<Unit>()
        val job = launch {
            AutomationRouteSession(
                routes = flow {
                    try {
                        emit(OutputRoute.Speaker)
                        awaitCancellation()
                    } finally {
                        platformClosed.set(true)
                    }
                },
                prepare = {},
                processRoute = { _, _, _ ->
                    routeProcessing.complete(Unit)
                    releaseProcessing.await()
                },
                onFailure = { throw AssertionError(it) },
                waitBeforeRetry = {},
            ).run(ProfileApplySource.AUTOMATION_START)
        }

        routeProcessing.await()
        assertFalse(platformClosed.get())
        releaseProcessing.complete(Unit)
        job.cancelAndJoin()
        assertTrue(platformClosed.get())
    }

    @Test
    fun eventsDuringASlowApplicationConflateToTheLatestRoute() = runBlocking {
        val wired = OutputRoute("wired", "有线耳机", OutputRouteType.WIRED_HEADSET)
        val bluetooth = OutputRoute("bluetooth", "蓝牙耳机", OutputRouteType.BLUETOOTH)
        val firstRouteStarted = CompletableDeferred<Unit>()
        val releaseFirstRoute = CompletableDeferred<Unit>()
        val latestRouteProcessed = CompletableDeferred<Unit>()
        val observedRoutes = mutableListOf<OutputRoute>()
        val job = launch {
            AutomationRouteSession(
                routes = flow {
                    emit(OutputRoute.Speaker)
                    firstRouteStarted.await()
                    emit(wired)
                    emit(bluetooth)
                    awaitCancellation()
                },
                prepare = {},
                processRoute = { route, force, source ->
                    observedRoutes += route
                    if (route == OutputRoute.Speaker) {
                        assertTrue(force)
                        firstRouteStarted.complete(Unit)
                        releaseFirstRoute.await()
                    } else {
                        assertFalse(force)
                        assertEquals(ProfileApplySource.ROUTE_CHANGE, source)
                        latestRouteProcessed.complete(Unit)
                    }
                },
                onFailure = { throw AssertionError(it) },
                waitBeforeRetry = {},
            ).run(ProfileApplySource.AUTOMATION_START)
        }

        firstRouteStarted.await()
        releaseFirstRoute.complete(Unit)
        latestRouteProcessed.await()
        job.cancelAndJoin()

        assertEquals(listOf(OutputRoute.Speaker, bluetooth), observedRoutes)
    }

    @Test
    fun retryDelayStartsAtOneSecondAndIsBoundedAtThirtySeconds() {
        assertEquals(1_000L, automationMonitorRetryDelay(0L))
        assertEquals(2_000L, automationMonitorRetryDelay(1L))
        assertEquals(30_000L, automationMonitorRetryDelay(29L))
        assertEquals(30_000L, automationMonitorRetryDelay(Long.MAX_VALUE))
        assertEquals(1_000L, automationMonitorRetryDelay(-1L))
    }
}
