package inflow

import inflow.utils.AtomicInt
import inflow.utils.TestItem
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class CacheTest : BaseTest() {

    @Test(timeout = 1_000L)
    fun `In-memory cache is working as expected`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            cacheInMemory(null)
        }

        var dataAuto: TestItem? = TestItem(Long.MIN_VALUE)
        var dataCache: TestItem? = TestItem(Long.MIN_VALUE)

        launch(job) {
            inflow.data().collect { dataAuto = it }
        }

        launch(job) {
            // Second cache listener, just to be sure
            inflow.data(autoRefresh = false).collect { dataCache = it }
        }

        delay(100L)

        assertNotNull(dataAuto, "Data is loaded -- auto")
        assertNotNull(dataCache, "Data is loaded -- cache")
        assertEquals(dataAuto, dataCache, "Same data is loaded")
    }

    @Test(timeout = 1_000L)
    fun `Cache is subscribed if has subscribers`() = runBlockingTestWithJob { job ->
        val cacheCalls = AtomicInt()

        val cache = flow { // Cold cache flow
            cacheCalls.getAndIncrement()

            emit(TestItem(-1))
            delay(100L)
            emit(TestItem(-2))
            delay(100L)
            emit(TestItem(-3))
            awaitCancellation()
        }

        val inflow = testInflow {
            cache(cache)
            cacheKeepSubscribedTimeout(200L)
        }

        // Launching endless subscription
        val collectorJob = launch {
            inflow.data(autoRefresh = false).collect()
        }

        // Getting first emitted item
        launch(job) {
            assertEquals(TestItem(-1), inflow.get(), "Receiving first item")
        }

        // Getting second emitted item after delay
        delay(100L)
        launch(job) {
            assertEquals(TestItem(-2), inflow.get(), "Receiving second item")
        }

        // Un-subscribing to reset cold cache flow
        collectorJob.cancel()

        // Getting third item within timeout interval, original cold cache is still subscribed
        delay(100L)
        launch(job) {
            assertEquals(TestItem(-3), inflow.get(), "Receiving third item")
        }

        // Getting first item after timeout interval, original cold cache should be re-subscribed
        delay(200L)
        launch(job) {
            assertEquals(TestItem(-1), inflow.get(), "Receiving first item again")
        }

        assertEquals(expected = 2, cacheCalls.get(), "Original cache is only subscribed twice")
    }

    @Test(timeout = 1_000L)
    fun `Cache can be slow`() = runBlockingTest {
        val flow = flow { // Cold cache flow
            delay(1000L)
            emit(TestItem(0))
        }

        val inflow = testInflow {
            cache(flow)
        }

        var item: TestItem? = null

        launch { item = inflow.get() }

        delay(500L)
        assertNull(item, "No item in the beginning")

        delay(500L)
        assertNotNull(item, "Item is finally loaded")
    }


    @Test
    fun `In-memory cache initialized only once`() = runBlockingTest {
        var count = 0
        val inflow = testInflow {
            cacheInMemoryDeferred {
                count++
                delay(100L)
                TestItem(-1L)
            }
            cacheKeepSubscribedTimeout(0L)
        }

        var item1: TestItem? = null
        launch { item1 = inflow.get() }
        delay(100L)
        assertNotNull(item1, "Item 1 is loaded")

        var item2: TestItem? = null
        launch { item2 = inflow.get() }
        delay(100L)
        assertNotNull(item2, "Item 2 is loaded")

        assertEquals(expected = 1, count, "Cache initialized is called only once")
    }

    @Test(expected = RuntimeException::class, timeout = 1_000L)
    fun `Cache can throw uncaught exception`() = runBlockingTest {
        val inflow = testInflow {
            cache(flow { throw RuntimeException() })
        }

        inflow.data().collect()
    }

}
