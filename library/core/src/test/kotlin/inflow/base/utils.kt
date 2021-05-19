@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.base

import inflow.Inflow
import inflow.InflowConfig
import inflow.State
import inflow.inflow
import inflow.utils.InflowLogger
import inflow.utils.now
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import kotlin.coroutines.ContinuationInterceptor


/**
 * Runs a test with specific job that is guaranteed to be cancelled in the end.
 *
 * Useful to run long-running tasks that should be canceled in the end of the test.
 */
@ExperimentalCoroutinesApi
fun runTest(testBody: suspend TestCoroutineScope.(Job) -> Unit) = runBlockingTest {
    val job = Job()
    try {
        testBody(job)
    } finally {
        job.cancel()
    }
}

fun <T> runReal(block: suspend CoroutineScope.() -> T) =
    runBlocking(Dispatchers.Default, block)


@ExperimentalCoroutinesApi
fun TestCoroutineScope.testInflow(
    block: InflowConfig<Int?>.() -> Unit
): Inflow<Int?> = inflow {
    dispatcher(testDispatcher)
    block()
}

@ExperimentalCoroutinesApi
val TestCoroutineScope.testDispatcher: CoroutineDispatcher
    get() = coroutineContext[ContinuationInterceptor] as CoroutineDispatcher

suspend fun maxDelay() = delay(Long.MAX_VALUE - 1L)

suspend fun Flow<State>.track(tracker: TestTracker) = collect {
    if (it is State.Loading) tracker.active++
    if (it is State.Idle) tracker.idle++
}

data class TestTracker(var active: Int = 0, var idle: Int = 0)


/**
 * Runs several iterations of same action at a constant rate of 4 actions per second.
 */
suspend fun runStressTest(
    runs: Int = STRESS_RUNS,
    block: suspend CoroutineScope.(Int) -> Unit
) {
    val wasVerbose = InflowLogger.verbose
    InflowLogger.verbose = false // Avoiding spamming in logs

    val start = now()
    coroutineScope {
        repeat(runs) { i ->
            launch {
                delay(i / 4L - (now() - start)) // Running at specific rate to have more races
                block(i)
            }
        }
    }

    InflowLogger.verbose = wasVerbose
}

@ExperimentalCoroutinesApi
suspend fun TestCoroutineScope.catchScopeException(
    block: suspend TestCoroutineScope.(CoroutineScope) -> Unit
): Throwable? {
    var caught: Throwable? = null
    val handler = CoroutineExceptionHandler { _, th -> caught = th }
    block(CoroutineScope(handler))
    return caught
}


fun getLogMessage(block: () -> Unit): String? = with(InflowLogger) {
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
