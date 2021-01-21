@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflow

import inflow.BaseTest
import inflow.cached
import inflow.emptyInflow
import inflow.utils.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class EmptyTest : BaseTest() {

    @Test
    fun `IF empty inflow THEN only initial value is returned`() = runTest {
        val inflow = emptyInflow(0)
        assertEquals(expected = 0, actual = inflow.cached(), "Initial value")
        assertEquals(expected = 0, actual = inflow.refresh().await(), "Refresh value is the same")
    }

    @Test
    fun `IF empty inflow with null THEN null is returned`() = runTest {
        val inflow = emptyInflow<Unit?>()
        assertNull(actual = inflow.cached(), "Value is null")
        assertNull(actual = inflow.refresh().await(), "Refreshed value is still null")
    }

}
