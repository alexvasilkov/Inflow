package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIn
import inflow.RefreshParam
import inflow.RefreshParam.Repeat
import inflow.cached
import inflow.fresh
import inflow.inflow
import inflow.utils.now
import inflow.utils.runTest
import inflow.utils.runThreads
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RefreshTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN data is loaded`() = runTest {
        val inflow = testInflow {}

        inflow.refresh()

        assertNull(inflow.cached(), "Starts with null")

        delay(100L)
        assertNotNull(inflow.cached(), "Item is loaded")
    }

    @Test
    fun `IF refresh with Repeat THEN loading is repeated`() = runTest {
        val inflow = testInflow {}

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
            cacheInMemory(-1)
            cacheExpiration(ExpiresIn(50L) { now() })
        }

        val item1 = inflow.fresh()
        assertEquals(expected = -1, actual = item1, "Cached item is returned as-is")

        val item2 = inflow.fresh(expiresIn = 100L)
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
            loader { throw RuntimeException() }
        }

        assertFailsWith<RuntimeException> {
            inflow.fresh()
        }
    }


    @Test
    fun `IF refresh is called with blocking loader THEN data is loaded`() = runThreads {
        val inflow = inflow {
            cacheInMemory { 0 }
            loader {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(50L)
                1
            }
        }

        inflow.refresh().join()

        delay(10L) // We have to delay to ensure cache data is propagated
        assertEquals(expected = 1, actual = inflow.cached(), "New item is loaded")
    }

}
