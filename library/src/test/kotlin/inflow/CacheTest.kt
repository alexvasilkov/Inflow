package inflow

import inflow.utils.TestItem
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
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
    fun `In-memory cache is used if no cache provided`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            cache = null
            cacheWriter = null
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

        assertNotNull(dataAuto, "Data is loaded -- auto")
        assertNotNull(dataCache, "Data is loaded -- cache")
        assertEquals(dataAuto, dataCache, "Same data is loaded")
    }

    @Test(timeout = 1_000L)
    fun `Cache is subscribed if has subscribers`() = runBlockingTestWithJob { job ->
        val flow = flow { // Cold cache flow
            emit(TestItem(-1))
            delay(100L)
            emit(TestItem(-2))
            delay(100L)
            emit(TestItem(-3))
        }

        val inflow = testInflow {
            cache = flow
            cacheKeepSubscribedTimeout = 200L
        }

        // Launching endless subscription
        val collectorJob = launch {
            inflow.data(autoRefresh = false).collect()
        }

        // Subscribing and getting first emitted item
        launch(job) {
            val item = inflow.data(autoRefresh = false).first()
            assertEquals(TestItem(-1), item, "Receiving first item")
        }

        // Subscribing and getting second emitted item after delay
        delay(100L)
        launch(job) {
            val item = inflow.data(autoRefresh = false).first()
            assertEquals(TestItem(-2), item, "Receiving second item")
        }

        // Un-subscribing to reset cold cache flow
        collectorJob.cancel()

        // Re-subscribing withing timeout interval, original cold cache should still be subscribed
        delay(100L)
        launch(job) {
            val item = inflow.data(autoRefresh = false).first()
            assertEquals(TestItem(-3), item, "Receiving third item")
        }

        // Re-subscribing after timeout interval, original cold cache should be re-subscribed
        delay(200L)
        launch(job) {
            val item = inflow.data(autoRefresh = false).first()
            assertEquals(TestItem(-1), item, "Receiving first item again")
        }
    }

    @Test(timeout = 1_000L)
    fun `Cache can be slow`() = runBlockingTest {
        val flow = flow { // Cold cache flow
            delay(1000L)
            emit(TestItem(0))
        }

        val inflow = testInflow {
            cache = flow
        }

        var item: TestItem? = null

        launch {
            item = inflow.data(autoRefresh = false).first()
        }

        delay(500L)
        assertNull(item, "No item in the beginning")

        delay(500L)
        assertNotNull(item, "Item is finally loaded")
    }

    @Test(expected = RuntimeException::class, timeout = 1_000L)
    fun `Cache can throw uncaught exception`() = runBlockingTest {
        val inflow = testInflow {
            cache = flow { throw RuntimeException() }
        }

        inflow.data().collect()
    }

}
