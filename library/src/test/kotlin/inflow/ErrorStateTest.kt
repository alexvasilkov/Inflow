package inflow

import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class ErrorStateTest : BaseTest() {

    @Test
    fun `Error is propagated`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            loader = {
                delay(100L)
                throw RuntimeException()
            }
        }

        var error: Throwable? = IllegalStateException()

        launch(job) {
            inflow.error().collect { error = it }
        }

        assertNull(error, "No error in the beginning")

        inflow.refresh()

        delay(100L)

        assertNotNull(error, "Error is collected")

        inflow.refresh()

        assertNull(error, "Error in cleared when starting loading")

        delay(100L)

        assertNotNull(error, "Error is collected again")
    }

    @Test
    fun `Error is propagated only once if refresh is forced`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            loader = {
                delay(100L)
                throw RuntimeException()
            }
        }

        var error: Throwable? = IllegalStateException()
        var errorsCount = 0

        launch(job) {
            inflow.error().collect {
                error = it
                if (it != null) errorsCount++
            }
        }

        assertNull(error, "No error in the beginning")

        inflow.refresh()

        delay(50L)

        inflow.refresh(repeatIfRunning = true)

        delay(100L)

        assertNull(error, "No error during second refresh")

        delay(50L)

        assertNotNull(error, "Error is collected in the end")
        assertEquals(expected = 1, actual = errorsCount, "Error is collected only once")
    }

}
