package inflow.inflow

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.utils.TestItem
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
class ConnectivityTest : BaseTest() {

    @Test
    fun `Connectivity forces data loading retry`() = runBlockingTestWithJob { job ->
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

        assertEquals(expected = 1, actual = counter, "Loading in the beginning")

        connectivityState.emit(false)
        connectivityState.emit(true)
        assertEquals(expected = 2, actual = counter, "Retry is called")
    }

    @Test
    fun `Connectivity does not force non-expired data update`() = runBlockingTestWithJob { job ->
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

        assertEquals(expected = 1, actual = counter, "Loading in the beginning")

        connectivityState.emit(false)
        connectivityState.emit(true)
        assertEquals(expected = 1, actual = counter, "No extra loading")
    }

    @Test
    fun `No connectivity should still call the update`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            connectivity(object : InflowConnectivity {
                override val connected = MutableStateFlow(false)
            })
        }

        // Running auto-refreshing
        var item: TestItem? = null
        launch(job) { inflow.data().collect { item = it } }

        delay(100L)
        assertNotNull(item, "Loading in the beginning")
    }

}
