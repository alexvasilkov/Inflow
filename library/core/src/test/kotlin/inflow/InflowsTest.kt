@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow

import inflow.base.BaseTest
import inflow.base.runTest
import inflow.base.testDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class InflowsTest : BaseTest() {

    @Test
    fun `IF inflow is requested with param THEN new inflow is created`() = runTest {
        val inflows = inflows(factory = { param: Int -> createInflow(param) })

        val inflow0 = inflows[0]
        assertEquals(expected = 0, actual = inflow0.cached(), "Inflow for 0 is used")

        val inflow42 = inflows[42]
        assertEquals(expected = 42, actual = inflow42.cached(), "Inflow for 42 is used")
    }

    @Test
    fun `IF cached inflow is removed THEN it is not cancelled`() = runTest {
        val inflows = inflows(
            factory = { param: Int -> createInflow(param) },
            cache = InflowsCache.create(maxSize = 1)
        )

        val inflow0 = inflows[0]
        inflows[1] // Requesting new inflow, the old one should be removed but not cancelled

        assertEquals(expected = 0, actual = inflow0.cached(), "Value for 0 is returned")
    }

    @Test
    fun `IF inflows are cached THEN cached inflows can accessed as snapshot`() = runTest {
        val inflows = inflows(factory = { param: Int -> createInflow(param) })

        val in0 = inflows[0]
        val in1 = inflows[1]

        assertEquals(expected = listOf(in0, in1), actual = inflows.snapshot(), "Snapshot is ok")
    }

    @Test
    fun `IF inflows is cleared THEN new inflows can be created again`() = runTest {
        val inflows = inflows(factory = { param: Int -> createInflow(param) })

        val inflow0 = inflows[0]
        assertEquals(expected = 0, actual = inflow0.cached(), "Value for 0 is returned")
        inflow0.refresh()
        assertEquals(expected = 1, actual = inflow0.cached(), "Refreshed value is returned")

        inflows.clear()
        val inflow0v2 = inflows[0]
        assertEquals(expected = 0, actual = inflow0v2.cached(), "New inflow is created")
    }

    @ExperimentalCoroutinesApi
    private fun TestCoroutineScope.createInflow(param: Int) = inflow<Int> {
        var count = param
        data(initial = param) { ++count }
        dispatcher(testDispatcher)
    }

}
