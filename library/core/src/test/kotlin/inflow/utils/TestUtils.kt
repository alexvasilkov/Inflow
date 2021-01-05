package inflow.utils

import inflow.Inflow
import inflow.InflowConfig
import inflow.Progress
import inflow.inflow
import inflow.loading
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.assertEquals

/**
 * Runs a test with specific job that is guaranteed to be cancelled in the end.
 *
 * Useful to run long-running tasks that should be canceled in the end of the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runTest(testBody: suspend TestCoroutineScope.(Job) -> Unit) = runBlockingTest {
    val job = Job()
    try {
        testBody(job)
    } finally {
        job.cancel()
    }
}

internal fun <T> runReal(block: suspend CoroutineScope.() -> T) = runBlocking(Dispatchers.IO, block)


@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestCoroutineScope.testInflow(block: InflowConfig<Int?>.() -> Unit): Inflow<Int?> =
    inflow {
        cacheDispatcher(testDispatcher)
        loadDispatcher(testDispatcher)
        block()
    }

@OptIn(ExperimentalStdlibApi::class, ExperimentalCoroutinesApi::class)
val TestCoroutineScope.testDispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher.Key] as CoroutineDispatcher


internal suspend fun Flow<Progress>.track(tracker: TestTracker) {
    collect {
        if (it === Progress.Active) tracker.active++
        if (it === Progress.Idle) tracker.idle++
    }
}

internal data class TestTracker(
    var active: Int = 0,
    var idle: Int = 0
)

internal suspend fun Inflow<*>.isIdle(): Boolean = !loading().first()

internal suspend fun Flow<Progress>.waitIdle() = first { it === Progress.Idle }


/**
 * Runs several iterations of same action at a constant rate of 4 actions per second.
 */
internal suspend fun runStressTest(
    logId: String,
    runs: Int,
    block: suspend CoroutineScope.(Int) -> Unit
) {
    val wasVerbose = inflowVerbose
    inflowVerbose = false // Avoiding spamming in logs

    val scope = CoroutineScope(Dispatchers.IO)
    val counter = AtomicInt()

    val start = now()
    for (i in 0 until runs) {
        scope.launch {
            delay(i / 4L - (now() - start)) // Running at specific rate to have more races
            block(i)
            counter.getAndIncrement()
        }
    }

    while (counter.get() != runs) {
        log(logId) { "Counter: ${counter.get()}" }
        delay(100L)
    }

    // Give it extra time to finish unfinished jobs
    delay(100L)

    assertEquals(expected = runs, actual = counter.get(), "All tasks finished")

    inflowVerbose = wasVerbose
}

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun TestCoroutineScope.catchScopeException(
    block: suspend TestCoroutineScope.(CoroutineScope) -> Unit
): Throwable? {
    var caught: Throwable? = null
    val handler = CoroutineExceptionHandler { _, th -> caught = th }
    block(CoroutineScope(handler))
    return caught
}


internal fun getLogMessage(block: () -> Unit): String? {
    val wasVerbose = inflowVerbose
    inflowVerbose = true
    val origLogger = inflowLogger

    var message: String? = null
    inflowLogger = { _, msg -> message = msg }

    block()

    inflowVerbose = wasVerbose
    inflowLogger = origLogger

    return message
}
