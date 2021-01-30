@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.utils

import inflow.Inflow
import inflow.InflowConfig
import inflow.STRESS_RUNS
import inflow.State
import inflow.inflow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals


/**
 * Runs a test with specific job that is guaranteed to be cancelled in the end.
 *
 * Useful to run long-running tasks that should be canceled in the end of the test.
 */
@ExperimentalCoroutinesApi
internal fun runTest(testBody: suspend TestCoroutineScope.(Job) -> Unit) = runBlockingTest {
    val job = Job()
    try {
        testBody(job)
    } finally {
        job.cancel()
    }
}

internal fun <T> runReal(block: suspend CoroutineScope.() -> T) =
    runBlocking(Dispatchers.Default, block)


@ExperimentalCoroutinesApi
internal fun TestCoroutineScope.testInflow(block: InflowConfig<Int?>.() -> Unit): Inflow<Int?> =
    inflow {
        cacheDispatcher(testDispatcher)
        loadDispatcher(testDispatcher)
        block()
        coroutineContext.plus(Dispatchers.Default)
    }

@ExperimentalCoroutinesApi
val TestCoroutineScope.testDispatcher: CoroutineDispatcher
    get() = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher


internal suspend fun Flow<State>.track(tracker: TestTracker) {
    collect {
        if (it is State.Loading) tracker.active++
        if (it is State.Idle) tracker.idle++
    }
}

internal data class TestTracker(
    var active: Int = 0,
    var idle: Int = 0
)


/**
 * Runs several iterations of same action at a constant rate of 4 actions per second.
 */
internal suspend fun runStressTest(
    runs: Int = STRESS_RUNS,
    block: suspend CoroutineScope.(Int) -> Unit
) {
    val wasVerbose = InflowLogger.verbose
    InflowLogger.verbose = false // Avoiding spamming in logs

    val scope = CoroutineScope(Dispatchers.Default)
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
        log("stress") { "Counter: ${counter.get()}" }
        delay(100L)
    }

    // Give it extra time to finish unfinished jobs
    delay(100L)

    assertEquals(expected = runs, actual = counter.get(), "All tasks finished")

    InflowLogger.verbose = wasVerbose
}

@ExperimentalCoroutinesApi
internal suspend fun TestCoroutineScope.catchScopeException(
    block: suspend TestCoroutineScope.(CoroutineScope) -> Unit
): Throwable? {
    var caught: Throwable? = null
    val handler = CoroutineExceptionHandler { _, th -> caught = th }
    block(CoroutineScope(handler))
    return caught
}


internal fun getLogMessage(block: () -> Unit): String? = with(InflowLogger) {
    val wasVerbose = verbose
    verbose = true
    val origLogger = logger

    var message: String? = null
    logger = { _, msg -> message = msg }

    block()

    verbose = wasVerbose
    logger = origLogger

    return message
}
