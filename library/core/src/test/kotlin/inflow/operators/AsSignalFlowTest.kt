package inflow.operators

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.internal.asSignalingFlow
import inflow.utils.runBlockingTestWithJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class AsSignalFlowTest : BaseTest() {

    @Test(timeout = 1_000L)
    fun `Run offline and repeat when re-connected`() = runBlockingTestWithJob { job ->
        val connected = MutableStateFlow(false)
        val connectivity = object : InflowConnectivity {
            override val connected = connected
        }

        launch {
            delay(100L)
            connected.emit(true)
            delay(100L)
            connected.emit(false)
            delay(100L)
            connected.emit(true)
        }

        var count = 0
        launch(job) { connectivity.asSignalingFlow().collect { count++ } }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 3, actual = count, "First run + 2 activations")
    }

    @Test(timeout = 1_000L)
    fun `Run online and repeat when re-connected`() = runBlockingTestWithJob { job ->
        val connected = MutableStateFlow(true)
        val connectivity = object : InflowConnectivity {
            override val connected = connected
        }

        launch {
            delay(100L)
            connected.emit(false)
            delay(100L)
            connected.emit(true)
        }

        var count = 0
        launch(job) { connectivity.asSignalingFlow().collect { count++ } }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 2, count, "First run + 1 activation")
    }

    @Test(timeout = 1_000L)
    fun `No connectivity provider`() = runBlockingTestWithJob { job ->
        var count = 0
        launch(job) { null.asSignalingFlow().collect { count++ } }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 1, actual = count, "Only emit once")
    }

}
