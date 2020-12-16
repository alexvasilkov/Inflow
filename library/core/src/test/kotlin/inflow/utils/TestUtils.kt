package inflow.utils

import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowConnectivity
import inflow.inflow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

/**
 * Runs a test with specific job that is guaranteed to be cancelled in the end.
 *
 * Useful to run long-running tasks that should be canceled in the end of the test.
 */
@ExperimentalCoroutinesApi
internal fun runBlockingTestWithJob(testBody: suspend TestCoroutineScope.(Job) -> Unit) {
    runBlockingTest {
        val job = Job()
        try {
            testBody(job)
        } finally {
            job.cancel()
        }
    }
}

@ExperimentalCoroutinesApi
internal fun TestCoroutineScope.testInflow(
    block: InflowConfig<TestItem?>.() -> Unit
): Inflow<TestItem?> = inflow {
    cacheInMemory(null)
    loader {
        delay(100L)
        TestItem(currentTime)
    }
    loadRetryTime(100L)

    connectivity(object : InflowConnectivity {
        override val connected = MutableStateFlow(true)
    })

    block()

    cacheDispatcher(testDispatcher)
    loadDispatcher(testDispatcher)
}

internal suspend fun Flow<Boolean>.track(tracker: TestTracker) {
    collect { if (it) tracker.start++ else tracker.end++ }
}

@OptIn(ExperimentalStdlibApi::class)
@ExperimentalCoroutinesApi
private val TestCoroutineScope.testDispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher.Key] as CoroutineDispatcher


internal data class TestTracker(
    var start: Int = 0,
    var end: Int = 0
)


internal suspend fun runStressTest(
    logId: String,
    runs: Int,
    block: suspend CoroutineScope.(Int) -> Unit
) {
    val wasVerbose = inflowVerbose
    inflowVerbose = false // Avoiding spamming in logs

    val scope = CoroutineScope(Dispatchers.IO)
    val counter = AtomicInteger(0)

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