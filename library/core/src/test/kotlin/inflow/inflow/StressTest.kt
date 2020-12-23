package inflow.inflow

import inflow.BaseTest
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.cache
import inflow.forceRefresh
import inflow.inflow
import inflow.utils.AtomicInt
import inflow.utils.isIdle
import inflow.utils.runStressTest
import inflow.utils.runThreads
import inflow.utils.waitIdle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF observe several inflows THEN no deadlocks`() = runThreads {
        runStressTest(logId, STRESS_RUNS) { i ->
            for (j in 0 until 2) {
                val inflow = inflow<Unit?> {
                    logId("$i/$j")
                    data(initial = null) { delay(50L); throw RuntimeException() }
                    retryTime(Long.MAX_VALUE)
                }

                val job = Job()

                // Subscribing to start auto refresh
                launch(job) { inflow.data().collect() }

                // Waiting till the end
                inflow.error().first { it != null }
                inflow.progress().waitIdle()

                job.cancel()

                assertTrue(inflow.isIdle(), "Finished loading: $i/$j")
                assertNotNull(inflow.error().first(), "Error is tracked: $i/$j")
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF refresh several inflows THEN no deadlocks`() = runThreads {
        runStressTest(logId, STRESS_RUNS) { i ->
            for (j in 0 until 2) {
                val inflow = inflow<Unit?> {
                    logId("$i/$j")
                    val memory = MutableSharedFlow<Unit?>(replay = 1).apply { tryEmit(null) }
                    data(
                        cache = memory,
                        // Delaying memory cache writer
                        writer = { delay(10L); memory.emit(it) },
                        loader = { delay(50L) }
                    )
                }

                // Scheduling a new refresh, it will force extra refresh every second time
                val job = launch {
                    delay(10L)
                    (if (j % 2 == 1) inflow.forceRefresh() else inflow.refresh()).join()
                }

                inflow.refresh().await()
                job.join()
                inflow.cache().first { it != null }

                assertTrue(inflow.isIdle(), "Loading is finished $i/$j")
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF observe single inflow from several threads THEN no deadlocks`() = runThreads {
        val cacheState = AtomicInt()

        val inflow = inflow<Unit?> {
            val memory = MutableSharedFlow<Unit?>(replay = 1).apply { tryEmit(null) }
            val cache = memory
                .onStart { cacheState.getAndIncrement() }
                .onCompletion { cacheState.decrementAndGet() }
            data(cache) { delay(100L); memory.emit(Unit) }
            keepCacheSubscribedTimeout(1L)
            retryTime(Long.MAX_VALUE)
        }

        runStressTest(logId, STRESS_RUNS) {
            inflow.data().first { it != null }
            inflow.error().first { it == null }
            inflow.progress().waitIdle()

            assertTrue(inflow.isIdle(), "Loading finished")
        }

        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")
    }

}
