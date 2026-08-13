package com.weich.daptune.domain

import kotlinx.coroutines.CancellationException

/** Captures operation failures without converting structured cancellation into a value. */
suspend inline fun <T> runSuspendCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Throwable) {
    Result.failure(error)
}
