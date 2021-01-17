package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.MemoryCacheWriter
import inflow.cache
import inflow.cached
import inflow.utils.catchScopeException
import inflow.utils.runTest
import inflow.utils.testDispatcher
import inflow.utils.testInflow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

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
            data(cache) {}
            keepCacheSubscribedTimeout(200L)
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
            // Cold cache flow
            data(flow { delay(100L); emit(0) }) {}
        }

        var item: Int? = null

        launch { item = inflow.cached() }

        delay(50L)
        assertNull(item, "No item in the beginning")

        delay(50L)
        assertNotNull(item, "Item is finally loaded")
    }


    @Test
    fun `IF cache flow throws exception THEN crash`() = runTest {
        val exception = RuntimeException()
        val caught = catchScopeException { scope ->
            val inflow = testInflow {
                data(flow { throw exception }) {}
                scope(scope)
            }

            // Cache read will throw cancellation exception but we only care about scope exception
            runCatching { inflow.cached() }
        }
        assertSame(exception, caught, "Exception is handled")
    }

    @Test
    fun `IF cache flow throws exception with await() THEN crash`() = runTest {
        val exception = RuntimeException()
        val caught = catchScopeException { scope ->
            val inflow = testInflow {
                data(flow { throw exception }) {}
                scope(scope)
            }

            // Await call will throw its own exception, but we only care about scope exception
            runCatching { inflow.refresh().await() }
        }
        assertSame(exception, caught, "Exception is handled")
    }

    @Test
    fun `IF cache writer throws exception THEN crash`() = runTest {
        val exception = RuntimeException()
        val caught = catchScopeException { scope ->
            val inflow = testInflow {
                data(cache = flowOf(0), writer = { throw exception }, loader = { 1 })
                scope(scope)
            }

            inflow.refresh()
        }
        assertSame(exception, caught, "Exception is handled")
    }

    @Test
    fun `IF cache writer throws exception (with flow loader) THEN crash`() = runTest {
        val exception = RuntimeException()
        val caught = catchScopeException { scope ->
            val inflow = testInflow {
                data(cache = flowOf(0), writer = { throw exception }, loaderFlow = { flowOf(1) })
                scope(scope)
            }

            inflow.refresh()
        }
        assertSame(exception, caught, "Exception is handled")
    }


    @Test
    fun `IF in-memory cache is used THEN data is cached`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }
        testCachedInMemory(inflow, this, job)
    }

    @Test
    fun `IF in-memory deferred cache is used THEN data is cached`() = runTest { job ->
        val inflow = testInflow {
            data(initial = { null }) { delay(100L); 0 }
        }
        testCachedInMemory(inflow, this, job)
    }

    private suspend fun testCachedInMemory(inflow: Inflow<Int?>, scope: CoroutineScope, job: Job) {
        var data: Int? = Int.MIN_VALUE
        scope.launch(job) { inflow.data().collect { data = it } }

        var cache: Int? = Int.MAX_VALUE
        scope.launch(job) { inflow.cache().collect { cache = it } }

        delay(100L)

        assertNotNull(data, "Data is loaded -- auto")
        assertNotNull(cache, "Data is loaded -- cache")
        assertEquals(data, cache, "Same data is loaded")
    }

    @Test
    fun `IF in-memory cache is used THEN it is initialized only once`() = runTest {
        var count = 0
        val inflow = testInflow {
            data(initial = { delay(100L); count++ }) { throw RuntimeException() }
            // delay() call above will switch to Default dispatcher, but it is not an expected case
            cacheDispatcher(testDispatcher)
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


    @Test
    fun `IF in-memory cache is manually initialized THEN initializer is not called`() = runTest {
        lateinit var writer: MemoryCacheWriter<Int?>
        var count = 0
        val inflow = testInflow {
            writer = data(initial = { count++ }) { throw RuntimeException() }
        }

        writer(-1)

        var item1: Int? = null
        launch { item1 = inflow.cached() }
        assertEquals(expected = -1, item1, "Custom item is loaded")

        assertEquals(expected = 0, count, "Cache is never initialized")
    }

}
