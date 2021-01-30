@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.Expires
import inflow.base.BaseTest
import inflow.base.getLogMessage
import inflow.base.runReal
import inflow.base.runTest
import inflow.base.testInflow
import inflow.cached
import inflow.inflow
import inflow.utils.now
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class InvalidationTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val provider = Expires.after<Int?>(1L) { if (it == -1) 0L else Long.MAX_VALUE }
        val inflow = testInflow {
            data(initial = -1) { 0 }
            invalidation(emptyValue = -2, provider = provider)
        }

        assertEquals(expected = -2, actual = inflow.cached(), "Empty value is returned")
    }

    @Test
    fun `IF empty value is not immediately expired THEN warning`() = runTest {
        val warning = getLogMessage {
            // Empty value (-1) is not expired according to ExpiresIfNull policy, it should
            // trigger a warning
            testInflow {
                data(initial = null) { 0 }
                expiration(Expires.ifNull())
                invalidation(emptyValue = -1, provider = Expires.ifNull())
            }
        }

        assertNotNull(warning) { "Log message is expected" }
        assertTrue(warning.startsWith("Warning:"), "Log message starts with warning")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val inflow = testInflow {
            data(initial = -1) { 0 }
            invalidation(emptyValue = null, provider = Expires.ifNull())
        }

        assertEquals(expected = -1, actual = inflow.cached(), "Item is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runReal {
        val start = now()
        val inflow = inflow<Long?> {
            data(start) { throw RuntimeException() }
            invalidation(emptyValue = null, provider = Expires.after(30L) { it ?: 0L })
        }

        assertEquals(expected = start, actual = inflow.cached(), "Orig item is emitted")

        delay(50L)
        assertNull(inflow.cached(), "Empty value is emitted")
    }

}
