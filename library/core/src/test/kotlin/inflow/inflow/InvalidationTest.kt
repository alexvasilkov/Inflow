package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.cached
import inflow.inflow
import inflow.utils.getLogMessage
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InvalidationTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val invalidIn = ExpiresIn<Int?>(1L) { if (it == -1) 0L else Long.MAX_VALUE }
        val inflow = testInflow {
            data(initial = -1) { 0 }
            invalidation(invalidIn = invalidIn, emptyValue = -2)
        }

        assertEquals(expected = -2, actual = inflow.cached(), "Empty value is returned")
    }

    @Test
    fun `IF empty value is not immediately expired THEN warning`() = runTest {
        val warning = getLogMessage {
            // Empty value (-1) is not expired according to ExpiresIfNull policy, it should
            // trigger a warning
            testInflow {
                expiration(ExpiresIfNull())
                invalidation(invalidIn = ExpiresIfNull(), emptyValue = -1)
            }
        }

        assertNotNull(warning) { "Log message is expected" }
        assertTrue(warning.startsWith("Warning:"), "Log message starts with warning")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val inflow = testInflow {
            data(initial = -1) { 0 }
            invalidation(invalidIn = ExpiresIfNull(), emptyValue = null)
        }

        assertEquals(expected = -1, actual = inflow.cached(), "Item is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runReal {
        val start = now()
        val inflow = inflow<Long?> {
            data(start) { throw RuntimeException() }
            invalidation(invalidIn = ExpiresIn(30L) { it ?: 0L }, emptyValue = null)
        }

        assertEquals(expected = start, actual = inflow.cached(), "Orig item is emitted")

        delay(50L)
        assertNull(inflow.cached(), "Empty value is emitted")
    }

}
