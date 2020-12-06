package inflow.utils

import inflow.Inflow
import inflow.InflowConfig
import inflow.InflowConnectivity
import inflow.inflow
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest

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
    connectivity = object : InflowConnectivity {
        override val connected = MutableStateFlow(true)
    }
    loader = {
        delay(100L)
        TestItem(currentTime)
    }
    loadRetryTime = 100L

    block()

    cacheDispatcher = testDispatcher
    loadDispatcher = testDispatcher
}

internal suspend fun Flow<Boolean>.track(tracker: TestTracker) {
    collect { if (it) tracker.start++ else tracker.end++ }
}

@OptIn(ExperimentalStdlibApi::class)
@ExperimentalCoroutinesApi
internal val TestCoroutineScope.testDispatcher: CoroutineDispatcher
    get() = coroutineContext[CoroutineDispatcher.Key] as CoroutineDispatcher


internal data class TestTracker(
    var start: Int = 0,
    var end: Int = 0
)
