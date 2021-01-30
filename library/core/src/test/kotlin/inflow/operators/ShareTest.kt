@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.operators

import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.internal.share
import inflow.utils.AtomicInt
import inflow.utils.log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ShareTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF timeout=100 THEN flow is subscribed only once`() {
        val count = testWithTimeout(100L)
        assertEquals(expected = 1, actual = count, "Cache should only be subscribed once")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF timeout=1 THEN flow is subscribed several times with no deadlocks`() {
        val count = testWithTimeout(1L)
        assertTrue(count >= 1, "Cache should be subscribed at least once")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF timeout=0 THEN flow is subscribed several times with no deadlocks`() {
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
    ): Int = runReal {
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

        val scope = CoroutineScope(EmptyCoroutineContext)
        val shared = cache.share(scope, Dispatchers.Default, keepSubscribedTimeout)

        runStressTest {
            // Calling shared flow several times to provoke more races
            shared.first()
            shared.first()
            shared.first()
        }

        // Give extra time to unsubscribe from the cache in the end
        delay(keepSubscribedTimeout)

        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")

        log(logId) { "Cache subscribed ${cacheCalls.get()} time(s)" }
        cacheCalls.get()
    }

}
