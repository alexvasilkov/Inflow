package inflow.inflow

import inflow.BaseTest
import inflow.forceRefresh
import inflow.unhandledError
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ErrorStateTest : BaseTest() {

    @Test
    fun `IF exception THEN error is collected`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { delay(100L); throw RuntimeException() }
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
    fun `IF unhandled error requested THEN error is only collected once`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { throw RuntimeException() }
        }

        var error1: Throwable? = null
        launch(job) { inflow.unhandledError().collect { error1 = it } }

        var error2: Throwable? = null
        launch(job) { inflow.unhandledError().collect { error2 = it } }

        assertNull(error1, "No error 1 in the beginning")
        assertNull(error2, "No error 2 in the beginning")

        inflow.refresh()
        assertNotNull(error1, "Error 1 is collected")
        assertNull(error2, "Error 2 is not collected")
    }

    @Test
    fun `IF refresh is forced THEN error is propagated only once`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { delay(100L); throw RuntimeException() }
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

        inflow.forceRefresh()

        delay(100L)

        assertNull(error, "No error during second refresh")

        delay(50L)

        assertNotNull(error, "Error is collected in the end")
        assertEquals(expected = 1, actual = errorsCount, "Error is collected only once")
    }

}
