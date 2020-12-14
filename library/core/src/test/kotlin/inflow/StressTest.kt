package inflow

import inflow.utils.AtomicInt
import inflow.utils.inflowVerbose
import inflow.utils.now
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class StressTest : BaseTest() {

    @Before
    fun setupLocal() {
        // Avoiding spamming in logs
        inflowVerbose = false
    }

    @Test(timeout = 10_000L)
    fun `Subscribe to cache and observe state`(): Unit = runBlocking(Dispatchers.IO) {
        for (i in 0..3_000) {
            launch {
                for (j in 0..4) {
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
    }

    @Test(timeout = 10_000L)
    fun `Can refresh the data blocking`(): Unit = runBlocking(Dispatchers.IO) {
        for (i in 0..3_000) {
            launch {
                for (j in 0..4) {
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
                        inflow.refreshBlocking(repeatIfRunning = j % 2 == 1)
                    }

                    inflow.refreshBlocking()
                    job.join()
                    inflow.data(autoRefresh = false).first { it != null }

                    assertFalse(inflow.loading().value, "Loading is finished $i/$j")
                }
            }
        }
    }

    @Test(timeout = 10_000L)
    fun `Can subscribe to inflow from several places`(): Unit = runBlocking(Dispatchers.IO) {
        val runs = 3_000
        val cacheState = AtomicInt()

        val inflow = inflow<Unit?> {
            cacheInMemory(null)
            cache(
                cache!!
                    .onStart { cacheState.getAndIncrement() }
                    .onCompletion { cacheState.decrementAndGet() }
            )
            cacheKeepSubscribedTimeout(1L)
            loader { delay(500L) }
            loadRetryTime(Long.MAX_VALUE)
        }

        val counter = AtomicInt()
        val start = now()
        for (i in 0 until runs) {
            launch {
                delay(i / 4L - (now() - start)) // Running at specific time
                inflow.data().first { it != null }
                inflow.error().first { it == null }
                inflow.loading().first { !it }

                assertFalse(inflow.loading().value, "Loading finished")

                counter.getAndIncrement()
            }
        }

        while (counter.get() != runs) delay(100L)

        delay(100L) // Give it some time to unsubscribe from the cache in the end

        assertEquals(expected = runs, actual = counter.get(), "All tasks finished")
        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")
    }

}
