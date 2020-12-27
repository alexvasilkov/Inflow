package inflow.operators

import inflow.BaseTest
import inflow.ExpiresIf
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.internal.emptyIfInvalid
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
    fun `IF becomes invalid THEN emit as-is and then emit empty value`() = runReal {
        val startTime = now()
        val orig = flowOf<Long?>(startTime)
        val invalidIn = ExpiresIn<Long?>(duration = 30L, loadedAt = { it ?: 0L })
        val flow = orig.emptyIfInvalid(logId, invalidIn, null)

        assertEquals(expected = startTime, actual = flow.first(), "Orig item is emitted")

        delay(50L)
        assertNull(flow.first(), "Empty value is emitted")
    }

    @Test
    fun `IF using dynamic expiration that never expires THEN never invalid`() = runTest { job ->
        var expired = false
        val orig = flowOf<Unit?>(Unit)
        val expiration = ExpiresIf<Unit?>(interval = 100L) { expired }
        val flow = orig.emptyIfInvalid(logId, expiration, null)

        var item: Unit? = null
        launch(job) {
            flow.collect { item = it }
        }

        delay(1000L)
        assertSame(expected = Unit, actual = item, "Empty value is not returned")

        expired = true
        delay(100L)
        assertNull(item, "Empty value is finally returned")
    }

}
