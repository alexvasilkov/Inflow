package inflow.inflow

import inflow.BaseTest
import inflow.cache
import inflow.cached
import inflow.inflow
import inflow.utils.assertCrash
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CacheTest : BaseTest() {

    @Test
    fun `IF inflow has subscribers THEN cache is subscribed`() = runTest { job ->
        var cacheCalls = 0

        val cache = flow { // Cold cache flow
            cacheCalls++

            emit(-1)
            delay(100L)
            emit(-2)
            delay(100L)
            emit(-3)
            awaitCancellation()
        }

        val inflow = testInflow {
            cache(cache)
            cacheKeepSubscribedTimeout(200L)
        }

        // Launching endless subscription
        val collectorJob = launch { inflow.cache().collect() }

        // Getting first emitted item
        launch(job) {
            assertEquals(expected = -1, actual = inflow.cached(), "Receiving first item")
        }

        // Getting second emitted item after delay
        delay(100L)
        launch(job) {
            assertEquals(expected = -2, actual = inflow.cached(), "Receiving second item")
        }

        // Un-subscribing to reset cold cache flow
        collectorJob.cancel()

        // Getting third item within timeout interval, original cold cache is still subscribed
        delay(100L)
        launch(job) {
            assertEquals(expected = -3, actual = inflow.cached(), "Receiving third item")
        }

        // Getting first item after timeout interval, original cold cache should be re-subscribed
        delay(200L)
        launch(job) {
            assertEquals(expected = -1, actual = inflow.cached(), "Receiving first item again")
        }

        assertEquals(expected = 2, cacheCalls, "Original cache is only subscribed twice")
    }

    @Test
    fun `IF cache is slow THEN data can still be collected`() = runTest {
        val inflow = testInflow {
            cache(
                flow { // Cold cache flow
                    delay(100L)
                    emit(0)
                }
            )
        }

        var item: Int? = null

        launch { item = inflow.cached() }

        delay(50L)
        assertNull(item, "No item in the beginning")

        delay(50L)
        assertNotNull(item, "Item is finally loaded")
    }

    @Test
    fun `IF cache throws exception THEN crash`() = runTest {
        assertCrash<RuntimeException> {
            val inflow = inflow {
                cache(flow<Unit> { throw RuntimeException() })
                cacheWriter {}
                loader {}
            }

            inflow.refresh()
        }
    }

    @Test
    fun `IF in-memory cache is used THEN data is cached`() = runTest { job ->
        val inflow = testInflow {
            cacheInMemory(null)
        }

        var data: Int? = Int.MIN_VALUE
        launch(job) { inflow.data().collect { data = it } }

        var cache: Int? = Int.MAX_VALUE
        launch(job) { inflow.cache().collect { cache = it } }

        delay(100L)

        assertNotNull(data, "Data is loaded -- auto")
        assertNotNull(cache, "Data is loaded -- cache")
        assertEquals(data, cache, "Same data is loaded")
    }

    @Test
    fun `IF in-memory cache is used THEN it is initialized only once`() = runTest {
        var count = 0
        val inflow = testInflow {
            cacheInMemoryDeferred {
                delay(100L)
                count++
            }
            cacheKeepSubscribedTimeout(0L)
        }

        var item1: Int? = null
        launch { item1 = inflow.cached() }
        delay(100L)
        assertNotNull(item1, "Item 1 is loaded")

        var item2: Int? = null
        launch { item2 = inflow.cached() }
        delay(100L)
        assertNotNull(item2, "Item 2 is loaded")

        assertEquals(expected = 1, count, "Cache initializer is called only once")
    }

}
