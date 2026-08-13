package com.weich.daptune.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CoroutineResultsTest {
    @Test
    fun capturesOrdinaryFailure() = runBlocking {
        val error = IllegalStateException("failed")

        val result = runSuspendCatching<Unit> { throw error }

        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun rethrowsCancellation() = runBlocking {
        val cancellation = CancellationException("cancelled")

        try {
            runSuspendCatching<Unit> { throw cancellation }
            fail("CancellationException must escape")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
            assertEquals("cancelled", actual.message)
        }
    }
}
