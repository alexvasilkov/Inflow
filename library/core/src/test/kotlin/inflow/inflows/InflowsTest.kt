@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflows

import inflow.BaseTest
import inflow.InflowsConfig
import inflow.cached
import inflow.inflows
import inflow.inflowsCache
import inflow.utils.runTest
import inflow.utils.testDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class InflowsTest : BaseTest() {

    @Test
    fun `IF inflows with no factory THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflows<Int, Int> {}
        }
    }

    @Test
    fun `IF inflow is requested with param THEN new inflow is created`() = runTest {
        val inflows = inflows<Int, Int> { initTest(this@runTest) }

        val inflow0 = inflows[0]
        assertEquals(expected = 0, actual = inflow0.cached(), "Inflow for 0 is used")

        val inflow42 = inflows[42]
        assertEquals(expected = 42, actual = inflow42.cached(), "Inflow for 42 is used")
    }

    @Test
    fun `IF cached inflow is removed THEN it is not cancelled`() = runTest {
        val inflows = inflows<Int, Int> {
            initTest(this@runTest)
            cache(inflowsCache(maxSize = 1))
        }

        val inflow0 = inflows[0]
        inflows[1] // Requesting new inflow, the old one should be removed but not cancelled

        assertEquals(expected = 0, actual = inflow0.cached(), "Value for 0 is returned")
    }

    @Test
    fun `IF inflows is cleared THEN new inflows can be created`() = runTest {
        val inflows = inflows<Int, Int> { initTest(this@runTest) }

        val inflow0 = inflows[0]
        assertEquals(expected = 0, actual = inflow0.cached(), "Value for 0 is returned")
        inflow0.refresh()
        assertEquals(expected = 1, actual = inflow0.cached(), "Refreshed value is returned")

        inflows.clear()
        val inflow0v2 = inflows[0]
        assertEquals(expected = 0, actual = inflow0v2.cached(), "New inflow is created")
    }


    private fun InflowsConfig<Int, Int>.initTest(scope: TestCoroutineScope) {
        builder { param ->
            var count = param
            data(initial = param) { ++count }

            cacheDispatcher(scope.testDispatcher)
            loadDispatcher(scope.testDispatcher)
        }
    }

}
