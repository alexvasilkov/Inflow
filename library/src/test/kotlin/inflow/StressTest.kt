package inflow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class StressTest : BaseTest() {

    @Test(timeout = 10_000L)
    fun `Subscribe to cache and observe state`(): Unit = runBlocking(Dispatchers.IO) {
        for (i in 0..100) {
            launch {
                for (j in 0..10) {
                    val inflow = inflow<Unit> {
                        loader = {
                            delay(50L)
                            throw RuntimeException()
                        }
                        loadRetryTime = Long.MAX_VALUE
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
        for (i in 0..100) {
            launch {
                for (j in 0..10) {
                    val inflow = inflow<Unit> {
                        loader = { delay(50L) }
                    }

                    // Scheduling a new refresh, it will force extra refresh every second time
                    launch {
                        delay(10L)
                        inflow.refreshBlocking(repeatIfRunning = j % 2 == 0)
                    }

                    inflow.refreshBlocking()
                    val item = inflow.data().first()
                    assertNotNull(item, "Item is loaded: $i/$j")
                }
            }
        }
    }

    @Test(timeout = 10_000L)
    fun `Can subscribe to inflow from several places`(): Unit = runBlocking(Dispatchers.IO) {
        val runs = 1000

        val inflow = inflow<Unit> {
            loader = { delay(50L) }
            loadRetryTime = Long.MAX_VALUE
        }

        val job = Job()
        val counter = AtomicInteger(0)

        for (i in 0 until runs) {
            launch(job) {
                inflow.data().first()
                inflow.error().first { it == null }
                inflow.loading().first { !it }

                assertFalse(inflow.loading().value, "Loading finished")

                counter.incrementAndGet()
            }
        }

        while (counter.get() != runs) delay(100L)

        assertEquals(expected = runs, actual = counter.get(), "All tasks finished")
        job.cancel()
    }

}
