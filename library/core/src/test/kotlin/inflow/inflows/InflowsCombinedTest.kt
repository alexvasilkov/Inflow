@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflows

import inflow.BaseTest
import inflow.Inflow
import inflow.Progress
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.asInflow
import inflow.cached
import inflow.inflowsCache
import inflow.utils.runReal
import inflow.utils.runStressTest
import inflow.utils.runTest
import inflow.utils.testDispatcher
import inflow.utils.waitIdle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class InflowsCombinedTest : BaseTest() {

    @Test
    fun `IF combined inflow with no factory THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            flowOf(0).asInflow<Int, Int> {}
        }
    }

    @Test
    fun `IF combined inflow THEN data outputs are combined`() = runTest { job ->
        val inflow = combinedInflow()
        val result = mutableListOf<Int>()
        launch(job) {
            inflow.data().collect { result += it }
        }
        delay(201L)
        assertEquals(expected = listOf(0, 100, 200), actual = result, "Params are emitted")
    }

    @Test
    fun `IF combined inflow THEN progress states are combined`() = runTest { job ->
        val inflow = combinedInflow()
        val result = mutableListOf<Progress>()
        launch(job) {
            inflow.progress().collect { result += it }
        }

        // Waiting for 0, 1, 2 params (which should be collected as a single Idle event)
        delay(201L)

        // Calling refresh to have Active + Idle events
        inflow.refresh()

        val expected = listOf(Progress.Idle, Progress.Active, Progress.Idle)
        assertEquals(expected, result, "Progress states are combined")
    }

    @Test
    fun `IF combined inflow THEN errors are combined`() = runTest { job ->
        val exception = RuntimeException()
        val inflow = combinedInflow(loader = { throw exception })
        val result = mutableListOf<Throwable?>()
        launch(job) {
            inflow.error().collect { result += it }
        }

        // Waiting for 0, 1, 2 params (which should have no errors)
        delay(201L)

        // Calling refresh to have an error
        inflow.refresh()

        // Waiting for 3 param (which should have no errors)
        delay(100L)

        // Expecting that first 3 subsequent `nulls` are collapsed into one
        val expected = listOf<Throwable?>(null, exception, null)
        assertEquals(expected, result, "Errors are combined")
    }

    @Test
    fun `IF combined inflow THEN can join the refresh`() = runTest {
        val inflow = combinedInflow()
        delay(201L)

        inflow.refresh().join()
        assertEquals(expected = 1, inflow.cached(), "Data for param 0 is refreshed")
    }

    @Test
    fun `IF combined inflow THEN can await the refresh`() = runTest {
        val inflow = combinedInflow()

        val item = inflow.refresh().await()
        assertEquals(expected = 1, item, "Data for param 0 is refreshed")
    }

    @Test
    fun `IF combined inflow AND small cache THEN new inflow created again`() = runTest { job ->
        var count = 0
        val inflow = flowOf(0, 1, 0).asInflow<Int, Int> {
            builder {
                count++
                data(cache = flowOf(0), loader = {})
            }
            cache(inflowsCache(maxSize = 1))
            dispatcher(testDispatcher)
        }

        launch(job) { inflow.data().collect() }

        assertEquals(expected = 3, actual = count, "New inflow is created")
    }

    @Test
    fun `IF combined inflow AND custom scope THEN can cancel the scope`() = runTest { job ->
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = flowOf(0, 1, 0).asInflow<Int, Int> {
            builder {
                data(cache = flowOf(0), loader = {})
            }
            dispatcher(testDispatcher)
            scope(scope)
        }
        scope.cancel()

        var awaitCancelled = false
        launch(job) {
            try {
                inflow.refresh().await()
            } catch (ce: CancellationException) {
                awaitCancelled = true
            }
        }
        assertTrue(awaitCancelled, "await() is immediately cancelled")
    }


    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF combined inflow observed from several threads THEN no deadlocks`() = runReal {
        val params = MutableStateFlow(0)
        val inflow = params.asInflow<Int, Int?> {
            builder { param ->
                data(initial = null) { param }
            }
        }

        runStressTest { i ->
            if (i % 100 == 0) params.value++
            inflow.data().first()
            inflow.error().first()
            inflow.progress().waitIdle()
        }

        inflow.data().first { it != null }

        assertEquals(expected = params.value, actual = inflow.cached(), "Last param is loaded")
    }


    private fun TestCoroutineScope.combinedInflow(
        loader: (Int) -> Int = { it }
    ): Inflow<Int> {
        val params = flow {
            var count = 0
            while (true) {
                emit(count)
                count += 100
                delay(100L)
            }
        }
        return params.asInflow {
            builder { param ->
                var count = param
                data(initial = param, loader = { loader(++count) })

                cacheDispatcher(testDispatcher)
                loadDispatcher(testDispatcher)
            }
            dispatcher(testDispatcher)
        }
    }

}
