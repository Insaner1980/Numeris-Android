package com.emmah.numeris.data

import kotlinx.coroutines.CancellationException

/** Commit each successful target independently and still try the remaining configured targets. */
internal suspend fun <T> syncTargets(
    targets: List<T>,
    sync: suspend (T) -> Int,
): Int {
    var written = 0
    var firstFailure: Throwable? = null
    for (target in targets) {
        runCatching { sync(target) }.fold(
            onSuccess = { written += it },
            onFailure = {
                if (it is CancellationException) throw it
                if (firstFailure == null) firstFailure = it
            },
        )
    }
    firstFailure?.let { throw it }
    return written
}
