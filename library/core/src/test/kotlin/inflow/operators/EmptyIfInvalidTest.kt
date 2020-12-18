package inflow.operators

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.internal.emptyIfInvalid
import inflow.utils.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertSame

class EmptyIfInvalidTest : BaseTest() {

    @Test
    fun `IF invalid THEN returns empty value`() = runTest {
        val orig = flowOf<Unit?>(null)
        val flow = orig.emptyIfInvalid(logId, ExpiresIfNull(), Unit)
        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }

    @Test
    fun `IF valid THEN returns as is`() = runTest {
        val orig = flowOf<Unit?>(Unit)
        val flow = orig.emptyIfInvalid(logId, ExpiresIfNull(), null)
        assertSame(expected = Unit, actual = flow.first(), "Empty value is returned")
    }


}
