package inflow.operators

import inflow.BaseTest
import inflow.utils.AtomicInt
import inflow.utils.inflowVerbose
import inflow.utils.log
import inflow.utils.now
import inflow.utils.share
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpShareTest : BaseTest() {

    @Before
    fun setupLocal() {
        // Avoiding spamming in logs
        inflowVerbose = true
    }

    @Test(timeout = 10_000L)
    fun `Infinite flow is subscribed only once if timeout == 100L`() {
        val count = testWithTimeout(100L)
        assertEquals(expected = 1, actual = count, "Cache should only be subscribed once")
    }

    @Test(timeout = 10_000L)
    fun `Finite flow is subscribed only once if timeout == 100L`() {
        val flow = flowOf(null)
        val count = testWithTimeout(100L, flow)
        assertEquals(expected = 1, actual = count, "Cache should only be subscribed once")
    }

    @Test(timeout = 10_000L)
    fun `Subscribed several times if timeout == 1L`() {
        val count = testWithTimeout(1L)
        assertTrue(count > 1, "Cache should be subscribed several times")
    }

    @Test(timeout = 10_000L)
    fun `Subscribed several times if timeout == 0L`() {
        val count = testWithTimeout(0L)
        assertTrue(count > 1, "Cache should be subscribed several times")
    }

    /**
     * Subscribes to single shared flow from several threads.
     * Returns number of time the original cache was actually subscribed.
     */
    private fun testWithTimeout(
        keepSubscribedTimeout: Long,
        flow: Flow<Unit?> = MutableStateFlow(null)
    ): Int = runBlocking(Dispatchers.IO) {
        val scope = CoroutineScope(Dispatchers.IO)

        val runs = 10_000
        val cacheState = AtomicInt()
        val cacheCalls = AtomicInt()

        val cache = flow
            .onStart {
                cacheState.getAndIncrement()
                cacheCalls.getAndIncrement()
            }
            .onCompletion {
                cacheState.decrementAndGet()
            }

        val shared = cache.share(scope, keepSubscribedTimeout)

        val counter = AtomicInt()

        val start = now()
        for (i in 0 until runs) {
            launch {
                delay(i / 4L - (now() - start)) // Running at specific time
                shared.first()
                counter.getAndIncrement()
            }
        }

        while (counter.get() != runs) {
            log("TEST") { "Counter: ${counter.get()}" }
            delay(200L)
        }

        // Give it some time to unsubscribe from the cache in the end
        delay(keepSubscribedTimeout + 100L)

        assertEquals(expected = runs, actual = counter.get(), "All tasks finished")
        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")

        log("TEST") { "Cache subscribed ${cacheCalls.get()} time(s)" }
        cacheCalls.get()
    }

}
