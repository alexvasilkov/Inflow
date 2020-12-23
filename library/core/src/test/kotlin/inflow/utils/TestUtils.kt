package inflow.utils

import inflow.Inflow
import inflow.InflowConfig
import inflow.Progress
import inflow.inflow
import inflow.loading
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlin.test.assertFailsWith

/**
 * Runs a test with specific job that is guaranteed to be cancelled in the end.
 *
 * Useful to run long-running tasks that should be canceled in the end of the test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun runTest(testBody: suspend TestCoroutineScope.(Job) -> Unit) {
    runBlockingTest {
        val job = Job()
        try {
            testBody(job)
        } finally {
            job.cancel()
        }
    }
}

internal fun <T> runThreads(block: suspend CoroutineScope.() -> T) =
    runBlocking(Dispatchers.IO, block)


@OptIn(ExperimentalCoroutinesApi::class)
internal fun TestCoroutineScope.testInflow(
    block: InflowConfig<Int?>.() -> Unit
): Inflow<Int?> = inflow {
    logId("TEST")
    var count = 0
    data(initial = null) { delay(100L); count++ }
    retryTime(100L)

    block()

    cacheDispatcher(testDispatcher)
    loadDispatcher(testDispatcher)
}

@OptIn(ExperimentalStdlibApi::class)
@ExperimentalCoroutinesApi
private val TestCoroutineScope.testDispatcher: CoroutineDispatcher
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


internal suspend inline fun <reified T : Throwable> assertCrash(block: () -> Unit) {
    // Using real threading along with setDefaultUncaughtExceptionHandler to receive errors
    // thrown inside coroutines. There is no other way to get internal errors without changing
    // Inflow API and allow setting custom coroutine context instead of just a dispatcher.
    val handler = Thread.getDefaultUncaughtExceptionHandler()

    var error: Throwable? = null
    Thread.setDefaultUncaughtExceptionHandler { _, e -> error = e }

    assertFailsWith(T::class) {
        block()
        delay(50L) // Waiting for error to propagate
        throw error!!
    }

    Thread.setDefaultUncaughtExceptionHandler(handler)
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
