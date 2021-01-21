@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflow

import inflow.BaseTest
import inflow.DataParam.CacheOnly
import inflow.LoadTracker
import inflow.Progress.Active
import inflow.Progress.Idle
import inflow.Progress.State
import inflow.loading
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class ProgressStateTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN progress is triggered`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) {
            inflow.progress().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        inflow.refresh()

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `IF data is subscribed THEN progress is triggered`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) {
            inflow.progress().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        // Briefly subscribing to trigger refresh
        launch { inflow.data().take(1).collect() }

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `IF data is subscribed with CacheOnly THEN progress is not triggered`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) { inflow.progress().track(tracker) }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        launch { inflow.data(CacheOnly).first() }

        delay(1000L)

        assertEquals(TestTracker(0, 1), tracker, "Loading never started")
    }

    @Test
    fun `IF state is tracked THEN progress state is triggered`() = runTest {
        lateinit var trackerOutside: LoadTracker
        val inflow = testInflow {
            data(initial = null) { tracker ->
                trackerOutside = tracker
                delay(100L)
                tracker.state(0.0, 1.0)
                delay(100L)
                tracker.state(0.5, 1.0)
                delay(100L)
                tracker.state(1.0, 1.0)
                delay(100L)
                1
            }
        }

        inflow.refresh()

        suspend fun active() = inflow.progress().first() as? Active
        suspend fun state() = inflow.progress().first() as? State
        suspend fun idle() = inflow.progress().first() as? Idle

        assertNotNull(active(), "Loading is started")
        delay(100L)
        assertEquals(State(0.0, 1.0), state(), "First state")
        delay(100L)
        assertEquals(State(0.5, 1.0), state(), "Second state")
        delay(100L)
        assertEquals(State(1.0, 1.0), state(), "Third state")
        delay(100L)
        assertNotNull(idle(), "Loading is finished")

        trackerOutside.state(0.0, 0.0)
        assertNull(state(), "Cannot track outside of the loader")
    }

    @Test
    fun `IF using loading extension THEN loading state is correct`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { tracker ->
                delay(100L)
                tracker.state(0.5, 1.0)
                delay(100L)
                1
            }
        }

        val states = mutableListOf<Boolean>()
        launch(job) { inflow.loading().collect { states += it } }

        inflow.refresh()

        delay(Long.MAX_VALUE - 1L)

        val expected = listOf(false, true, false)
        assertEquals(expected, states, "All states are correct")
    }

    @Test
    fun `IF has state THEN rate is correctly calculated`() {
        assertEquals(expected = 0.0, actual = State(1.0, 0.0).rate(), "Cannot divide by 0")

        assertEquals(expected = 0.0, actual = State(-1.0, 1.0).rate(), "Cannot be negative")

        assertEquals(expected = 1.0, actual = State(2.0, 1.0).rate(), "Cannot be bigger than 1")

        val state = State(1.0, 2.0)
        assertEquals(expected = state.current / state.total, actual = state.rate(), "Rate")
    }

}
