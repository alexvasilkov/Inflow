package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.inflow
import inflow.latest
import inflow.utils.TestItem
import inflow.utils.now
import inflow.utils.runTest
import inflow.utils.runThreads
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class InvalidationTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val empty = TestItem(-1L)
        val inflow = testInflow {
            cacheInvalidation(invalidIn = ExpiresIfNull(), emptyValue = empty)
        }

        assertSame(expected = empty, actual = inflow.latest(), "Empty value is returned")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val item = TestItem(-1L)
        val inflow = testInflow {
            cacheInMemory(item)
            cacheInvalidation(invalidIn = ExpiresIfNull(), emptyValue = null)
        }

        assertSame(expected = item, actual = inflow.latest(), "Empty value is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runThreads {
        val first = TestItem(now())
        val inflow = inflow {
            cacheInMemory(first)
            cacheInvalidation(invalidIn = ExpiresIn(30L), emptyValue = null)
            loader { throw RuntimeException() }
        }

        assertSame(expected = first, actual = inflow.latest(), "Orig item is emitted")

        delay(35L)
        assertNull(inflow.latest(), "Empty value is emitted")
    }

}
