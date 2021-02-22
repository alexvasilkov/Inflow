@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.operators

import inflow.LoadTracker
import inflow.State
import inflow.base.AtomicInt
import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.base.runTest
import inflow.base.testDispatcher
import inflow.internal.Loader
import inflow.utils.log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class LoaderTest : BaseTest() {

    @Test
    fun `IF success THEN success state`() = runTest {
        val loader = createLoader { delay(100L) }
        loader.load()

        assertSame(State.Loading.Started, loader.state.value, "In loading state")
        delay(100L)
        assertSame(State.Idle.Success, loader.state.value, "Loading finished")
    }

    @Test
    fun `IF exception THEN error state`() = runTest {
        val exception = RuntimeException()
        val loader = createLoader { delay(100L); throw exception }
        loader.load()

        assertSame(State.Loading.Started, loader.state.value, "In loading state")
        delay(100L)
        val errorState = loader.state.value as? State.Idle.Error
        assertNotNull(errorState, "In error state")
        assertSame(expected = exception, actual = errorState.throwable, "Exception is tracked")
    }

    @Test
    fun `IF started several times THEN one action runs at a time`() = runTest {
        val loader = createLoader { delay(100L) }

        val job1 = loader.load()

        delay(50L)
        val job2 = loader.load()
        assertSame(job1, job2, "Same job if still running")

        delay(50L)
        val job3 = loader.load()
        assertNotSame(job1, job3, "New job if loading finished")
    }

    private fun TestCoroutineScope.createLoader(action: suspend (LoadTracker) -> Unit) =
        Loader(logId, this, testDispatcher, action)


    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF join() THEN only one action runs at a time`() = runReal {
        val loads = AtomicInt()
        val loader = Loader(logId, this, Dispatchers.Default) {
            delay(100L)
            loads.getAndIncrement()
        }

        runStressTest { loader.load().join() }

        log(logId) { "Loads: ${loads.get()}" }
        // There must be much more than one loading event (around STRESS_RUNS / 4 / 100), but it is
        // impossible to predict the exact amount because of timings, so we'll just check it's > 1.
        assertTrue(loads.get() > 1, "One action should run at a time")
    }

}
