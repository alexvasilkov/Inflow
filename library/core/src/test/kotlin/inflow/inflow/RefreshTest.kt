package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.latest
import inflow.utils.TestItem
import inflow.utils.runTest
import inflow.utils.runThreads
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RefreshTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN data is loaded`() = runTest {
        val inflow = testInflow {}

        inflow.refresh()

        assertNull(inflow.latest(), "Starts with null")

        delay(100L)
        assertNotNull(inflow.latest(), "Item is loaded")
    }

    @ExperimentalCoroutinesApi
    @Test
    fun `IF refresh is called with repeatIfRunning=true THEN loading is repeated`() = runTest {
        val inflow = testInflow {}

        inflow.refresh()

        // Forcing second refresh
        delay(50L)
        inflow.refresh(repeatIfRunning = true)

        assertNull(inflow.latest(), "Starts with null")

        // First item is loaded
        delay(50L)
        val item2 = inflow.latest()
        assertNotNull(item2, "Item is loaded")
        assertEquals(expected = currentTime, item2.loadedAt, "Fresh item is loaded")

        // Second item is loaded
        delay(100L)
        val item3 = inflow.latest()
        assertNotNull(item3, "Item is loaded")
        assertEquals(expected = currentTime, item3.loadedAt, "Fresh item is loaded")
    }

    @Test
    fun `IF refresh is called with blocking loader THEN data is loaded`() = runThreads {
        val inflow = inflow {
            cacheInMemory { TestItem(0L) }
            loader {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(50L)
                TestItem(1L)
            }
        }

        inflow.refresh().join()

        delay(10L) // We have to delay to ensure cache data is propagated
        assertEquals(expected = TestItem(1L), actual = inflow.latest(), "New item is loaded")
    }

}
