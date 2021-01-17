package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIn
import inflow.RefreshParam
import inflow.RefreshParam.Repeat
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.cached
import inflow.forceRefresh
import inflow.fresh
import inflow.inflow
import inflow.refreshIfExpired
import inflow.utils.AtomicInt
import inflow.utils.isIdle
import inflow.utils.log
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runStressTest
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RefreshTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN data is loaded`() = runTest {
        val inflow = testInflow {
            var count = 0
            data(initial = null) { delay(100L); count++ }
        }

        inflow.refresh()

        assertNull(inflow.cached(), "Starts with null")

        delay(100L)
        assertNotNull(inflow.cached(), "Item is loaded")
    }

    @Test
    fun `IF refresh with loader flow is called THEN data is loaded`() = runTest {
        val inflow = testInflow {
            val memory = MutableSharedFlow<Int?>(replay = 1).apply { tryEmit(null) }
            data(
                cache = memory,
                writer = { memory.emit(it) },
                loaderFlow = {
                    flow {
                        emit(0)
                        delay(100L)
                        emit(1)
                    }
                }
            )
            keepCacheSubscribedTimeout(0L)
        }

        assertNull(inflow.cached(), "Starts with null")

        inflow.refresh()

        assertEquals(expected = 0, inflow.cached(), "First value loaded")
        assertFalse(inflow.isIdle(), "Still in progress")
        delay(100L)
        assertEquals(expected = 1, inflow.cached(), "Second value loaded")
    }

    @Test
    fun `IF refresh with Repeat THEN loading is repeated`() = runTest {
        val inflow = testInflow {
            var count = 0
            data(initial = null) { delay(100L); count++ }
        }

        inflow.refresh()

        // Forcing second refresh
        delay(50L)
        inflow.refresh(Repeat)

        assertNull(inflow.cached(), "Starts with null")

        // First item is loaded
        delay(50L)
        val item2 = inflow.cached()
        assertNotNull(item2, "Item is loaded")
        assertEquals(expected = 0, actual = item2, "Fresh item is loaded")

        // Second item is loaded
        delay(100L)
        val item3 = inflow.cached()
        assertNotNull(item3, "Item is loaded")
        assertEquals(expected = 1, actual = item3, "Fresh item is loaded")
    }

    @Test
    fun `IF refresh with IfExpiresIn THEN loading is triggered only if expired`() = runTest {
        val inflow = testInflow {
            data(initial = -1) { 0 }
            expiration(ExpiresIn(50L) { now() })
            keepCacheSubscribedTimeout(0L)
        }

        val item1 = inflow.refreshIfExpired().await()
        assertEquals(expected = -1, actual = item1, "Cached item is returned as-is")

        val item2 = inflow.refreshIfExpired(expiresIn = 100L).await()
        assertEquals(expected = 0, actual = item2, "New item is loaded")
    }

    @Test
    fun `IF IfExpiresIn with negative value THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            RefreshParam.IfExpiresIn(-1L)
        }
    }

    @Test
    fun `IF refresh with IfExpiresIn has error THEN error is thrown by await()`() = runTest {
        val inflow = testInflow {
            data(initial = null) { throw RuntimeException() }
            keepCacheSubscribedTimeout(0L)
        }

        assertFailsWith<RuntimeException> {
            inflow.fresh()
        }
    }


    @Test
    fun `IF refresh is called with blocking loader THEN data is loaded`() = runReal {
        val inflow = inflow<Int> {
            data(initial = 0) {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(50L)
                1
            }
            keepCacheSubscribedTimeout(0L)
        }

        inflow.refresh().join()

        delay(10L) // We have to delay to ensure cache data is propagated
        assertEquals(expected = 1, actual = inflow.cached(), "New item is loaded")
    }


    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF refresh with Repeat and await() THEN all waiters get same result`() = runReal {
        val loads = AtomicInt()
        val inflow = inflow<Int> {
            data(initial = -1) { delay(100L); loads.getAndIncrement() }
            keepCacheSubscribedTimeout(0L)
        }

        var commonResult: Int? = null
        runStressTest {
            val result = inflow.forceRefresh().await()
            synchronized(inflow) { if (commonResult == null) commonResult = result }
            // All waiters should receive the latest loaded item
            assertEquals(commonResult, result, "All waiters get same result")
        }

        log(logId) { "Loads: ${loads.get()}" }
        assertEquals(loads.get() - 1, commonResult, "All waiters get latest result")
    }

}
