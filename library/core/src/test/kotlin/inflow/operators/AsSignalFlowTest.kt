package inflow.operators

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.internal.asSignalingFlow
import inflow.utils.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class AsSignalFlowTest : BaseTest() {

    @Test
    fun `IF offline THEN start as online and repeat when re-connected`() = runTest { job ->
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

    @Test
    fun `IF online THEN start as online and repeat when re-connected`() = runTest { job ->
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

    @Test
    fun `IF no connectivity provider THEN start as online`() = runTest { job ->
        var count = 0
        launch(job) { null.asSignalingFlow().collect { count++ } }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 1, actual = count, "Only emit once")
    }

}
