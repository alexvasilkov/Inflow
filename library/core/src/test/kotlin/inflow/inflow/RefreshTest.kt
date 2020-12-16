package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.latest
import inflow.utils.TestItem
import inflow.utils.testInflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class RefreshTest : BaseTest() {

    @Test
    fun `Can refresh the data`() = runBlockingTest {
        val inflow = testInflow {}

        inflow.refresh()

        assertNull(inflow.latest(), "Starts with null")

        delay(100L)
        assertNotNull(inflow.latest(), "Item is loaded")
    }

    @Test
    fun `Can refresh the data with forced repeat`() = runBlockingTest {
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

    @Test(timeout = 1_000L)
    fun `Can refresh the data blocking -- real threading`(): Unit = runBlocking(Dispatchers.IO) {
        val inflow = inflow {
            cacheInMemory { TestItem(0L) }
            @Suppress("BlockingMethodInNonBlockingContext")
            loader {
                Thread.sleep(50L)
                TestItem(1L)
            }
        }

        inflow.refresh().join()

        delay(10L) // We have to delay to ensure cache data is propagated
        assertEquals(expected = TestItem(1L), actual = inflow.latest(), "New item is loaded")
    }

}
