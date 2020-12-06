package inflow.operators

import inflow.BaseTest
import inflow.InflowConnectivity
import inflow.utils.repeatWhenConnected
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@ExperimentalCoroutinesApi
class OpRepeatWhenConnectedTest : BaseTest() {

    @Test
    fun `Run offline and repeat when re-connected`() = runBlockingTest {
        val data = flowOf(1, 2, 3)

        val connectivity = object : InflowConnectivity {
            override val connected = flow {
                emit(false)
                delay(100L)
                emit(true)
                delay(100L)
                emit(false)
                delay(100L)
                emit(true)
            }
        }

        val result = mutableListOf<Int>()
        data.repeatWhenConnected(connectivity).toList(result)

        // First 1, 2, 3 should be called as is even if no connectivity.
        // Then we have 2 "connected" signals which should repeat last known value.
        val expected = listOf(1, 2, 3, 3, 3)

        assertEquals(expected, result, "Result flow")
    }

    @Test
    fun `Run online and repeat when re-connected`() = runBlockingTest {
        val data = flowOf(1, 2, 3)

        val connectivity = object : InflowConnectivity {
            override val connected = flow {
                emit(true)
                delay(100L)
                emit(false)
                delay(100L)
                emit(true)
            }
        }

        val result = mutableListOf<Int>()
        data.repeatWhenConnected(connectivity).toList(result)

        // First 1, 2, 3 should be called as is since we are connected.
        // Then we have 1 "connected" signal which should repeat last known value.
        val expected = listOf(1, 2, 3, 3)

        assertEquals(expected, result, "Result flow")
    }

    @Test
    fun `No connectivity provider`() = runBlockingTest {
        val data = flowOf(1, 2, 3)
        assertSame(data, data.repeatWhenConnected(null), "Same flow")
    }

}
