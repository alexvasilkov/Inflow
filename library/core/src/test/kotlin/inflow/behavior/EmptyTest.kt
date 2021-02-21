@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.State.Idle
import inflow.base.BaseTest
import inflow.base.runTest
import inflow.cached
import inflow.emptyInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `IF empty inflow THEN only initial state is emitted`() = runTest { job ->
        val inflow = emptyInflow<Unit?>()
        var nonInitial = false
        launch(job) { inflow.refreshState().collect { if (it != Idle.Initial) nonInitial = true } }
        launch(job) { inflow.data().collect() }

        delay(Long.MAX_VALUE - 1L)
        assertFalse(nonInitial, "Never active after auto refresh")

        inflow.refresh().join()
        assertFalse(nonInitial, "Never active after manual refresh")
    }

}
