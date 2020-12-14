package inflow.operators

import inflow.BaseTest
import inflow.utils.TestTracker
import inflow.utils.runBlockingTestWithJob
import inflow.utils.runWithState
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class OpRunWithStateTest : BaseTest() {

    private val logId = "TEST_ID"

    @Test
    fun `Only one action can run at a time`() = runBlockingTest {
        val loading = MutableStateFlow(false)
        val error = MutableStateFlow<Throwable?>(null)
        val action = suspend { delay(1000L) }
        val retryForced = MutableStateFlow(false)

        launch {
            val result = runWithState(logId, loading, error, retryForced, force = false, action)
            assertTrue(result, "First action is called")
        }
        launch {
            val result = runWithState(logId, loading, error, retryForced, force = false, action)
            assertFalse(result, "Second action is not called")
        }
        delay(1000L)
        launch {
            val result = runWithState(logId, loading, error, retryForced, force = false, action)
            assertTrue(result, "Third action is called")
        }
    }

    @Test
    fun `Can force extra refresh if already running`() = runBlockingTestWithJob { job ->
        val loading = MutableStateFlow(false)
        val error = MutableStateFlow<Throwable?>(null)
        val action = suspend {
            delay(100L)
            throw RuntimeException()
        }
        val retryForced = MutableStateFlow(false)

        val loadingsTracker = TestTracker()
        launch(job) { loading.track(loadingsTracker) }

        var errorsCount = 0
        launch(job) { error.collect { if (it != null) errorsCount++ } }

        // Launching regular refresh
        launch {
            val result = runWithState(logId, loading, error, retryForced, force = false, action)
            assertTrue(result, "First action is called")
        }

        delay(50L)
        assertNull(error.value, "No error in the middle")
        assertEquals(TestTracker(1, 1), loadingsTracker, "Loading in the middle")

        // Forcing second refresh while the first one is in the middle
        launch {
            val result = runWithState(logId, loading, error, retryForced, force = true, action)
            assertFalse(result, "Second action is not called")
        }

        // Forcing third refresh while the first one is in the middle
        launch {
            val result = runWithState(logId, loading, error, retryForced, force = true, action)
            assertFalse(result, "Third action is not called")
        }

        delay(50L)
        assertNull(error.value, "No error in the end of first refresh")
        assertEquals(TestTracker(1, 1), loadingsTracker, "Loading in the end of first refresh")

        delay(100L)
        assertNotNull(error.value, "Error in the end of second refresh")
        assertEquals(TestTracker(1, 2), loadingsTracker, "Not loading in the end of second refresh")

        assertEquals(expected = 1, actual = errorsCount, "Error should only be emitted once")
    }

    @Test
    fun `Loading state is propagated`() = runBlockingTest {
        val loading = MutableStateFlow(false)

        launch { runWithStateDefaults(loading = loading) }

        assertTrue(loading.value, "In loading state")

        delay(100L)

        assertFalse(loading.value, "Loading finished")
    }

    @Test
    fun `Error is propagated`() = runBlockingTest {
        val error = MutableStateFlow<Throwable?>(null)
        val exception = RuntimeException()
        val action = suspend {
            delay(100L)
            throw exception
        }

        launch { runWithStateDefaults(error = error, action = action) }

        assertNull(error.value, "No error in the beginning")

        delay(100L)
        assertSame(expected = exception, actual = error.value, "Error is sent")
    }

    @Test
    fun `Error is cleared on start`() = runBlockingTest {
        val error = MutableStateFlow<Throwable?>(RuntimeException())

        launch { runWithStateDefaults(error = error) }

        assertNull(error.value, "No error in the beginning")

        delay(1000L)
        assertNull(error.value, "No error in the end")
    }


    @Test
    fun `Loader scope is cancelled`(): Unit = runBlockingTest {
        val error = MutableStateFlow<Throwable?>(null)
        val exception = RuntimeException()
        val action = suspend {
            delay(50L)
            throw exception
        }

        val job = launch { runWithStateDefaults(error = error, action = action) }

        launch {
            delay(25L)
            job.cancel()
            delay(50L)
            assertSame(expected = exception, actual = error.value, "Exception in the end")
        }
    }


    private suspend fun runWithStateDefaults(
        loading: MutableStateFlow<Boolean> = MutableStateFlow(false),
        error: MutableStateFlow<Throwable?> = MutableStateFlow(null),
        retryForced: MutableStateFlow<Boolean> = MutableStateFlow(false),
        force: Boolean = false,
        action: suspend () -> Unit = suspend { delay(100L) }
    ) = runWithState(logId, loading, error, retryForced, force, action)

}
