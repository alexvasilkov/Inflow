@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow

import inflow.State.Idle
import inflow.State.Loading
import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.base.runTest
import inflow.base.testDispatcher
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
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class MergedInflowsTest : BaseTest() {

    @Test
    fun `IF parametrized inflow THEN data outputs are combined`() = runTest { job ->
        val inflow = parametrizedInflow()
        val result = mutableListOf<Int>()
        launch(job) {
            inflow.data().collect { result += it }
        }
        delay(201L)
        assertEquals(expected = listOf(0, 100, 200), actual = result, "Params are emitted")
    }

    @Test
    fun `IF parametrized inflow THEN progress states are combined`() = runTest { job ->
        val inflow = parametrizedInflow()
        val result = mutableListOf<State>()
        launch(job) {
            inflow.refreshState().collect { result += it }
        }

        // Waiting for 0, 100, 200 params
        delay(201L)

        // Calling refresh to have a loading event
        inflow.refresh()

        val expected = listOf(Idle.Initial, Loading.Started, Idle.Success)
        assertEquals(expected, result, "Progress states are combined")
    }

    @Test
    fun `IF parametrized inflow THEN can join the refresh`() = runTest {
        val inflow = parametrizedInflow()
        inflow.refresh().join()
        assertEquals(expected = 1, inflow.cached(), "Data for param 0 is refreshed")
    }

    @Test
    fun `IF parametrized inflow THEN can await the refresh`() = runTest {
        val inflow = parametrizedInflow()
        val item = inflow.refresh().await()
        assertEquals(expected = 1, item, "Data for param 0 is refreshed")
    }

    @Test
    fun `IF parametrized inflow AND small cache THEN new inflow created again`() = runTest { job ->
        var count = 0
        val factory = { _: Int ->
            inflow<Int> {
                count++
                data(cache = flowOf(0), loader = {})
            }
        }
        val inflow = inflows(factory, cache = InflowsCache.create(maxSize = 1))
            .mergeBy(params = flowOf(0, 1, 0), testDispatcher)

        launch(job) { inflow.data().collect() }

        assertEquals(expected = 3, actual = count, "New inflow is created")
    }

    @Test
    fun `IF parametrized inflow AND custom scope THEN can cancel the scope`() = runTest { job ->
        val scope = CoroutineScope(EmptyCoroutineContext)
        val factory = { _: Int ->
            inflow<Int> {
                data(cache = flowOf(0), loader = {})
            }
        }
        val inflow = inflows(factory).mergeBy(params = flowOf(0, 1, 0), testDispatcher, scope)
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
    fun `IF parametrized inflow observed from several threads THEN no deadlocks`() = runReal {
        val params = MutableStateFlow(0)
        val inflow = inflows(
            factory = { param: Int ->
                inflow<Int?> { data(initial = null, loader = { param }) }
            }
        ).mergeBy(params)

        runStressTest { i ->
            if (i % 100 == 0) params.value++
            inflow.data().first { it != null }
            inflow.refreshState().first { it is Idle }
        }

        inflow.data().first { it != null }

        assertEquals(expected = params.value, actual = inflow.cached(), "Last param is loaded")
    }


    /**
     * Returns an inflow built on top of (0, 100, 200, ...) params sequence that emits every 100ms.
     * Calling "refresh" on resulting inflow will increase the param by one, e.g. (201, 202, ...).
     */
    private fun TestCoroutineScope.parametrizedInflow(
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

        val factory = { param: Int ->
            inflow<Int> {
                var count = param
                data(initial = param, loader = { loader(++count) })
                dispatcher(testDispatcher)
            }
        }
        return inflows(factory).mergeBy(params, testDispatcher)
    }

}
