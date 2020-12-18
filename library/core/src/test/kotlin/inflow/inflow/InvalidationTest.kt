package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.inflow
import inflow.latest
import inflow.utils.now
import inflow.utils.runTest
import inflow.utils.runThreads
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InvalidationTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val inflow = testInflow {
            cacheInvalidation(invalidIn = ExpiresIfNull(), emptyValue = -1)
        }

        assertEquals(expected = -1, actual = inflow.latest(), "Empty value is returned")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val inflow = testInflow {
            cacheInMemory(-1)
            cacheInvalidation(invalidIn = ExpiresIfNull(), emptyValue = null)
        }

        assertEquals(expected = -1, actual = inflow.latest(), "Item is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runThreads {
        val start = now()
        val inflow = inflow {
            cacheInMemory(start)
            cacheInvalidation(invalidIn = ExpiresIn<Long?>(30L) { it ?: 0L }, emptyValue = null)
            loader { throw RuntimeException() }
        }

        assertEquals(expected = start, actual = inflow.latest(), "Orig item is emitted")

        delay(35L)
        assertNull(inflow.latest(), "Empty value is emitted")
    }

}
