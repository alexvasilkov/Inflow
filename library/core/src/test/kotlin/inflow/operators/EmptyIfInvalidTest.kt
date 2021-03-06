@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.operators

import inflow.Expires
import inflow.base.BaseTest
import inflow.base.runReal
import inflow.base.runTest
import inflow.internal.emptyIfInvalid
import inflow.utils.now
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@ExperimentalCoroutinesApi
class EmptyIfInvalidTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val orig = flowOf<Unit?>(null)
        val flow = orig.emptyIfInvalid(logId, Expires.ifNull(), Unit)

        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val orig = flowOf<Unit?>(Unit)
        val flow = orig.emptyIfInvalid(logId, Expires.ifNull(), null)

        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runReal {
        val startTime = now()
        val orig = flowOf<Long?>(startTime)
        val invalidIn = Expires.after<Long?>(duration = 30L, loadedAt = { it ?: 0L })
        val flow = orig.emptyIfInvalid(logId, invalidIn, null)

        assertEquals(expected = startTime, actual = flow.first(), "Orig item is emitted")

        delay(50L)
        assertNull(flow.first(), "Empty value is emitted")
    }

}
