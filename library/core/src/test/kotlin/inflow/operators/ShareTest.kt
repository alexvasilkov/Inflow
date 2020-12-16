package inflow.operators

import inflow.BaseTest
import inflow.internal.SharedFlowProvider
import inflow.utils.runStressTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareTest : BaseTest() {

    @Test(timeout = 15_000L)
    fun `Infinite flow is subscribed only once if timeout == 100L`() {
        val count = testWithTimeout(100L)
        assertEquals(expected = 1, actual = count, "Cache should only be subscribed once")
    }

    @Test(timeout = 15_000L)
    fun `Finite flow is subscribed only once if timeout == 100L`() {
        val flow = flowOf(null)
        val count = testWithTimeout(100L, flow)
        assertEquals(expected = 1, actual = count, "Cache should only be subscribed once")
    }

    @Test(timeout = 15_000L)
    fun `Subscribed several times if timeout == 1L`() {
        val count = testWithTimeout(1L)
        assertTrue(count > 1, "Cache should be subscribed several times")
    }

    @Test(timeout = 15_000L)
    fun `Subscribed several times if timeout == 0L`() {
        val count = testWithTimeout(0L)
        assertTrue(count > 1, "Cache should be subscribed several times")
    }

    /**
     * Subscribes to single shared flow from several threads.
     * Returns number of time the original cache was actually subscribed.
     * Uses real blocking calls to catch race conditions.
     */
    private fun testWithTimeout(
        keepSubscribedTimeout: Long,
        flow: Flow<Unit?> = MutableStateFlow(null)
    ): Int = runBlocking(Dispatchers.IO) {
        val runs = 5_000

        val cacheState = AtomicInteger(0)
        val cacheCalls = AtomicInteger(0)

        val cache = flow
            .onStart {
                cacheState.incrementAndGet()
                cacheCalls.incrementAndGet()
            }
            .onCompletion {
                cacheState.decrementAndGet()
            }

        val scope = CoroutineScope(Dispatchers.IO)
        val shared = SharedFlowProvider(cache, scope, keepSubscribedTimeout).shared

        runStressTest(logId, runs) { shared.first() }

        // Give extra time to unsubscribe from the cache in the end
        delay(keepSubscribedTimeout)

        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")

        println("Cache subscribed ${cacheCalls.get()} time(s)")
        cacheCalls.get()
    }

}
