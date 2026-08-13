package com.weich.daptune.platform.routing

import com.weich.daptune.core.model.OutputRoute
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedRouteFlowTest {
    @Test
    fun concurrentConsumersShareOnePlatformSubscriptionAndClearStaleReplay() = runBlocking {
        val totalSubscriptions = AtomicInteger(0)
        val activeSubscriptions = AtomicInteger(0)
        val releaseFirstSnapshot = CompletableDeferred<Unit>()
        val source = flow {
            totalSubscriptions.incrementAndGet()
            activeSubscriptions.incrementAndGet()
            try {
                releaseFirstSnapshot.await()
                emit(OutputRoute.Speaker)
                awaitCancellation()
            } finally {
                activeSubscriptions.decrementAndGet()
            }
        }
        val sharingScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val sharedRoutes = source.shareRouteEventsIn(sharingScope)

        try {
            val consumers = List(4) {
                async(start = CoroutineStart.UNDISPATCHED) { sharedRoutes.first() }
            }
            withTimeout(1_000L) {
                while (activeSubscriptions.get() != 1) yield()
            }
            releaseFirstSnapshot.complete(Unit)

            assertEquals(List(4) { OutputRoute.Speaker }, consumers.awaitAll())
            assertEquals(1, totalSubscriptions.get())

            withTimeout(1_000L) {
                while (activeSubscriptions.get() != 0) yield()
            }
            withTimeout(1_000L) {
                while (sharedRoutes.replayCache.isNotEmpty()) yield()
            }
            assertEquals(OutputRoute.Speaker, sharedRoutes.first())
            assertEquals(2, totalSubscriptions.get())
        } finally {
            sharingScope.cancel()
        }
    }
}
