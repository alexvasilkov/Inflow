@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.Inflow
import inflow.InflowCombined
import inflow.State
import inflow.base.BaseTest
import inflow.base.runTest
import inflow.base.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
class CombineTest : BaseTest() {

    @Test
    fun `IF getting combined data and state THEN combinations are correct`() {
        testCombined(expect = null, expectState = State.Idle.Initial) { cacheAndState() }
        testCombined(expect = 0, expectState = State.Idle.Success) { dataAndState() }
    }

    private fun testCombined(
        expect: Int?,
        expectState: State,
        combined: Inflow<Int?>.() -> Flow<InflowCombined<Int?>>
    ) = runTest { job ->
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }

        var result: InflowCombined<Int?>? = null
        launch(job) { inflow.combined().collect { result = it } }

        assertNotNull(result, "Combined result is received")
        assertEquals(expected = expect, result!!.data, "Value")
        assertEquals(expected = expectState, result!!.refresh, "Refresh state")
        assertEquals(expected = null, result!!.loadNext, "Load next state")
    }

}
