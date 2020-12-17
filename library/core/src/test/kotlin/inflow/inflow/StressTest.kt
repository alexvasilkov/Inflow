package inflow.inflow

import inflow.BaseTest
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.inflow
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class StressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Subscribe to cache and observe state`(): Unit = runBlocking(Dispatchers.IO) {

        runStressTest(logId, STRESS_RUNS) { i ->
            for (j in 0 until 2) {
                val inflow = inflow {
                    logId("$i/$j")
                    cacheInMemory(null)
                    loader {
                        delay(50L)
                        throw RuntimeException()
                    }
                    loadRetryTime(Long.MAX_VALUE)
                }

                val job = Job()

                // Subscribing to start auto refresh
                launch(job) { inflow.data().collect() }

                // Waiting till the end
                inflow.error().first { it != null }
                inflow.loading().first { !it }

                job.cancel()

                assertFalse(inflow.loading().value, "Finished loading: $i/$j")
                assertNotNull(inflow.error().value, "Error is tracked: $i/$j")
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Can refresh the data blocking`(): Unit = runBlocking(Dispatchers.IO) {
        runStressTest(logId, STRESS_RUNS) { i ->
            for (j in 0 until 2) {
                val inflow = inflow<Unit?> {
                    logId("$i/$j")
                    cacheInMemory(null)

                    // Delaying memory cache writer
                    val origWriter = requireNotNull(cacheWriter)
                    cacheWriter { delay(10L); origWriter.invoke(it) }

                    loader { delay(50L) }
                }

                // Scheduling a new refresh, it will force extra refresh every second time
                val job = launch {
                    delay(10L)
                    inflow.refresh(repeatIfRunning = j % 2 == 1).join()
                }

                inflow.refresh().await()
                job.join()
                inflow.data(autoRefresh = false).first { it != null }

                assertFalse(inflow.loading().value, "Loading is finished $i/$j")
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Can subscribe to inflow from several places`(): Unit = runBlocking(Dispatchers.IO) {
        val cacheState = AtomicInteger(0)

        val inflow = inflow<Unit?> {
            cacheInMemory(null)
            cache(
                cache!!
                    .onStart { cacheState.getAndIncrement() }
                    .onCompletion { cacheState.decrementAndGet() }
            )
            cacheKeepSubscribedTimeout(1L)
            loader { delay(100L) }
            loadRetryTime(Long.MAX_VALUE)
        }

        runStressTest(logId, STRESS_RUNS) {
            inflow.data(autoRefresh = true).first { it != null }
            inflow.error().first { it == null }
            inflow.loading().first { !it }

            assertFalse(inflow.loading().value, "Loading finished")
        }

        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")
    }

}
