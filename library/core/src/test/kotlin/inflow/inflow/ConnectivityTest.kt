package inflow.inflow

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.utils.TestItem
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
            cacheInMemory(null)
            loader {
                counter++
                throw RuntimeException()
            }
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

        var counter = 0L
        val inflow = testInflow {
            cacheInMemory(null)
            loader { TestItem(counter++) }
            connectivity(object : InflowConnectivity {
                override val connected = connectivityState
            })
        }

        // Running auto-refreshing
        launch(job) { inflow.data().collect() }

        assertEquals(expected = 1, actual = counter, "Loaded on subscription")

        connectivityState.emit(false)
        connectivityState.emit(true)
        assertEquals(expected = 1, actual = counter, "No extra loading")
    }

    @Test
    fun `IF not connected THEN still call initial update`() = runTest { job ->
        val inflow = testInflow {
            connectivity(object : InflowConnectivity {
                override val connected = MutableStateFlow(false)
            })
        }

        // Running auto-refreshing
        var item: TestItem? = null
        launch(job) { inflow.data().collect { item = it } }

        delay(100L)
        assertNotNull(item, "Loaded on subscription")
    }

}
