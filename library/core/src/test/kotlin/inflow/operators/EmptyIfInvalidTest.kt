package inflow.operators

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.internal.emptyIfInvalid
import inflow.utils.now
import inflow.utils.runTest
import inflow.utils.runThreads
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class EmptyIfInvalidTest : BaseTest() {

    @Test
    fun `IF invalid THEN emit empty value`() = runTest {
        val orig = flowOf<Unit?>(null)
        val flow = orig.emptyIfInvalid(logId, ExpiresIfNull(), Unit)

        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }

    @Test
    fun `IF valid THEN emit as-is`() = runTest {
        val orig = flowOf<Unit?>(Unit)
        val flow = orig.emptyIfInvalid(logId, ExpiresIfNull(), null)

        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }

    @Test
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runThreads {
        val startTime = now()
        val orig = flowOf<Long?>(startTime)
        val invalidIn = ExpiresIn<Long?>(duration = 30L, loadedAt = { this ?: 0L })
        val flow = orig.emptyIfInvalid(logId, invalidIn, null)

        assertEquals(expected = startTime, actual = flow.first(), "Orig item is emitted")

        delay(35L)
        assertNull(flow.first(), "Empty value is emitted")
    }

}
