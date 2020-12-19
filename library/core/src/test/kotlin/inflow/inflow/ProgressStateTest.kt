package inflow.inflow

import inflow.BaseTest
import inflow.DataParam.CacheOnly
import inflow.Progress.Active
import inflow.Progress.Idle
import inflow.Progress.State
import inflow.ProgressTracker
import inflow.loading
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ProgressStateTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN progress is triggered`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {}

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
        val inflow = testInflow {}

        launch(job) {
            inflow.progress().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        // Briefly subscribing to trigger refresh
        launch(job) { inflow.data().take(1).collect() }

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `IF data is subscribed with CacheOnly THEN progress is not triggered`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {}

        launch(job) { inflow.progress().track(tracker) }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        launch(job) { inflow.data(CacheOnly).first() }

        delay(1000L)

        assertEquals(TestTracker(0, 1), tracker, "Loading never started")
    }

    @Test
    fun `IF state is tracked THEN progress state is triggered`() = runTest {
        lateinit var trackerOutside: ProgressTracker
        val inflow = testInflow {
            loader { tracker ->
                trackerOutside = tracker
                delay(100L)
                tracker.state(0f, 1f)
                delay(100L)
                tracker.state(0.5f, 1f)
                delay(100L)
                tracker.state(1f, 1f)
                delay(100L)
                1
            }
        }

        inflow.refresh()

        fun active() = inflow.progress().value as? Active
        fun state() = inflow.progress().value as? State
        fun idle() = inflow.progress().value as? Idle

        assertNotNull(active(), "Loading is started")
        delay(100L)
        assertEquals(State(0f, 1f), state(), "First state")
        delay(100L)
        assertEquals(State(0.5f, 1f), state(), "Second state")
        delay(100L)
        assertEquals(State(1f, 1f), state(), "Third state")
        delay(100L)
        assertNotNull(idle(), "Loading is finished")

        trackerOutside.state(0f, 0f)
        assertNull(state(), "Cannot track outside of the loader")
    }

    @Test
    fun `IF using loading extension THEN loading state is correct`() = runTest { job ->
        val inflow = testInflow {
            loader { tracker ->
                delay(100L)
                tracker.state(0.5f, 1f)
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
        assertEquals(expected = 0f, actual = State(1f, 0f).rate(), "Cannot divide by 0")

        assertEquals(expected = 0f, actual = State(-1f, 1f).rate(), "Cannot be negative")

        assertEquals(expected = 1f, actual = State(2f, 1f).rate(), "Cannot be bigger than 1")

        val state = State(1f, 2f)
        assertEquals(expected = state.current / state.total, actual = state.rate(), "Rate")
    }

}
