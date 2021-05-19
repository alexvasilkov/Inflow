@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.DataProvider
import inflow.LoadParam
import inflow.LoadTracker
import inflow.State
import inflow.State.Idle
import inflow.State.Loading
import inflow.StateParam
import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.TestTracker
import inflow.base.maxDelay
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.base.runTest
import inflow.base.testInflow
import inflow.base.track
import inflow.cached
import inflow.inflow
import inflow.refreshError
import inflow.refreshing
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
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
class StateTest : BaseTest() {

    @Test
    fun `IF refresh is called THEN loading state is updated`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) {
            inflow.refreshState().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        inflow.refresh()

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `IF load next is called THEN loading state is updated`() = runTest { job ->
        var loadNextCalled = false
        val inflow = testInflow {
            data = DataProvider(
                cache = flowOf(0),
                refresh = {},
                loadNext = { loadNextCalled = true }
            )
        }

        val states = mutableListOf<State>()
        launch(job) {
            inflow.stateInternal(StateParam.LoadNextState).collect { states += it }
        }

        inflow.loadInternal(LoadParam.LoadNext)

        assertTrue(loadNextCalled, "LoadNext is called")

        val expectedStates = listOf(Idle.Initial, Loading.Started, Idle.Success)
        assertEquals(expectedStates, states, "LoadNext states are correct")
    }

    @Test
    fun `IF data is subscribed THEN loading state is updated`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) {
            inflow.refreshState().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        // Briefly subscribing to trigger refresh
        launch { inflow.data().take(1).collect() }

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `IF cache is subscribed THEN loading state is not updated`() = runTest { job ->
        val tracker = TestTracker()
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        launch(job) { inflow.refreshState().track(tracker) }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        launch { inflow.cached() }

        delay(1000L)

        assertEquals(TestTracker(0, 1), tracker, "Loading never started")
    }

    @Test
    fun `IF loading progress is tracked THEN progress state is triggered`() = runTest {
        lateinit var trackerOutside: LoadTracker
        val inflow = testInflow {
            data(initial = null) { tracker ->
                trackerOutside = tracker
                delay(100L)
                tracker.progress(0.0, 1.0)
                delay(100L)
                tracker.progress(0.5, 1.0)
                delay(100L)
                tracker.progress(1.0, 1.0)
                delay(100L)
                1
            }
        }

        inflow.refresh()

        suspend fun started() = inflow.refreshState().first() as? Loading.Started
        suspend fun progress() = inflow.refreshState().first() as? Loading.Progress
        suspend fun idle() = inflow.refreshState().first() as? Idle

        assertNotNull(started(), "Loading is started")

        delay(100L)
        val state1 = assertNotNull(progress())
        assertTrue(state1.current == 0.0 && state1.total == 1.0, "First state")

        delay(100L)
        val state2 = assertNotNull(progress())
        assertTrue(state2.current == 0.5 && state2.total == 1.0, "Second state")

        delay(100L)
        val state3 = assertNotNull(progress())
        assertTrue(state3.current == 1.0 && state3.total == 1.0, "Third state")

        delay(100L)
        assertNotNull(idle(), "Loading is finished")

        trackerOutside.progress(0.0, 0.0)
        assertNull(progress(), "Cannot track outside of the loader")
    }

    @Test
    fun `IF using refreshing extension THEN resulting state is correct`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { tracker ->
                delay(100L)
                tracker.progress(0.5, 1.0)
                delay(100L)
                1
            }
        }

        val states = mutableListOf<Boolean>()
        launch(job) { inflow.refreshing().collect { states += it } }

        inflow.refresh()
        maxDelay()

        val expected = listOf(false, true, false)
        assertEquals(expected, states, "All states are correct")
    }

    @Test
    fun `IF tracking progress THEN progress state is correctly calculated`() {
        assertEquals(expected = 0.0, Loading.Progress(1.0, 0.0).state(), "Cannot divide by 0")

        assertEquals(expected = 0.0, Loading.Progress(-1.0, 1.0).state(), "Cannot be negative")

        assertEquals(expected = 1.0, Loading.Progress(2.0, 1.0).state(), "Cannot be bigger than 1")

        val progress = Loading.Progress(1.0, 2.0)
        assertEquals(expected = progress.current / progress.total, progress.state(), "State")
    }


    @Test
    fun `IF exception THEN error is collected`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { delay(100L); throw RuntimeException() }
        }

        var error: Throwable? = null

        launch(job) {
            inflow.refreshState().collect { if (it is Idle.Error) error = it.throwable }
        }

        assertNull(error, "No error in the beginning")
        inflow.refresh()
        delay(100L)
        assertNotNull(error, "Error is collected")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF unhandled error is requested THEN errors are only collected once`() = runReal {
        var lastError: RuntimeException? = null
        val inflow = inflow<Unit?> {
            data(initial = null) { throw RuntimeException().also { lastError = it } }
        }

        val jobs = mutableListOf<Job>()
        val errors = Collections.synchronizedList(mutableListOf<Throwable>())

        repeat(1_000) {
            jobs += launch {
                inflow.refreshError().collect { errors.add(it) }
            }
        }

        runStressTest { inflow.refresh().join() }

        jobs.forEach(Job::cancel)

        val noDuplicates = errors.toSet()

        assertEquals(errors.size, noDuplicates.size, "Each error is only collected once")
        assertTrue(noDuplicates.contains(lastError!!), "Last error is collected")
    }

}
