package inflow.inflow

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.cached
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ConnectivityTest : BaseTest() {

    @Test
    fun `IF error in loader THEN connectivity triggers retry`() = runTest { job ->
        val connectivityState = MutableStateFlow(true)

        var counter = 0
        val inflow = testInflow {
            data(initial = null) { counter++; throw RuntimeException() }
            connectivity(object : InflowConnectivity {
                override val connected = connectivityState
            })
        }

        // Running auto-refreshing
        launch(job) { inflow.data().collect() }

        assertEquals(expected = 1, actual = counter, "Loaded on subscription")

        connectivityState.emit(false)
        connectivityState.emit(true)
        assertEquals(expected = 2, actual = counter, "Retry is called")
    }

    @Test
    fun `IF data is not expired THEN connectivity does not force retry`() = runTest { job ->
        val connectivityState = MutableStateFlow(true)

        val inflow = testInflow {
            data(initial = null) { 0 }
            connectivity(object : InflowConnectivity {
                override val connected = connectivityState
            })
        }

        // Running auto-refreshing
        launch(job) { inflow.data().collect() }

        delay(100L)
        assertEquals(expected = 0, actual = inflow.cached(), "Loaded on subscription")

        connectivityState.emit(false)
        connectivityState.emit(true)
        assertEquals(expected = 0, actual = inflow.cached(), "No extra loading")
    }

    @Test
    fun `IF not connected THEN still call initial update`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { 0 }
            connectivity(object : InflowConnectivity {
                override val connected = MutableStateFlow(false)
            })
        }

        // Running auto-refreshing
        var item: Int? = null
        launch(job) { inflow.data().collect { item = it } }

        assertNotNull(item, "Loaded on subscription")
    }

}
