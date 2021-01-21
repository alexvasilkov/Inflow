package inflow.inflow

import inflow.BaseTest
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.forceRefresh
import inflow.inflow
import inflow.unhandledError
import inflow.utils.runReal
import inflow.utils.runStressTest
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
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
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF unhandled error requested THEN error is only collected once`() = runReal {
        var lastError: RuntimeException? = null
        val inflow = inflow<Unit?> {
            data(initial = null) { lastError = RuntimeException(); throw lastError!! }
        }

        val jobs = mutableListOf<Job>()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(1_000) {
            jobs += launch {
                inflow.unhandledError().collect { errors.add(it) }
            }
        }

        runStressTest { inflow.refresh().join() }

        jobs.forEach(Job::cancel)

        val noDuplicates = errors.toSet()

        assertEquals(errors.size, noDuplicates.size, "Each error is only collected once")
        assertTrue(noDuplicates.contains(lastError!!), "Last error is collected")
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
