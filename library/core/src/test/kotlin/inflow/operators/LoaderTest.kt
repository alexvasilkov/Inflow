package inflow.operators

import inflow.BaseTest
import inflow.LoadTracker
import inflow.Progress
import inflow.internal.Loader
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testDispatcher
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

@ExperimentalCoroutinesApi
class LoaderTest : BaseTest() {

    @Test
    fun `IF loaded THEN progress state is tracked`() = runTest {
        val loader = createLoader { delay(100L) }
        loader.load(repeatIfRunning = false)

        assertSame(Progress.Active, loader.progress.value, "In loading state")
        delay(100L)
        assertSame(Progress.Idle, loader.progress.value, "Loading finished")
    }

    @Test
    fun `IF exception THEN progress state is tracked`() = runTest {
        val loader = createLoader { delay(100L); throw RuntimeException() }
        loader.load(repeatIfRunning = false)

        assertSame(Progress.Active, loader.progress.value, "In loading state")
        delay(100L)
        assertSame(Progress.Idle, loader.progress.value, "Loading finished")
    }

    @Test
    fun `IF exception THEN error is tracked`() = runTest {
        val exception = RuntimeException()
        val loader = createLoader { delay(100L); throw exception }
        loader.load(repeatIfRunning = false)

        assertNull(loader.error.value, "No error in the beginning")
        delay(100L)
        assertSame(expected = exception, actual = loader.error.value, "Error is sent")
    }

    @Test
    fun `IF started THEN error is cleared`() = runTest {
        val loader = createLoader { delay(100L); throw RuntimeException() }

        loader.load(repeatIfRunning = false)
        delay(100L)
        assertNotNull(loader.error.value, "Error is detected")

        loader.load(repeatIfRunning = false)
        assertNull(loader.error.value, "Error is cleared on start")
    }


    @Test
    fun `IF started several times THEN one action runs at a time`() = runTest {
        val loader = createLoader { delay(100L) }

        val job1 = loader.load(repeatIfRunning = false)

        delay(50L)
        val job2 = loader.load(repeatIfRunning = false)
        assertSame(job1, job2, "Same job if still running")

        delay(50L)
        val job3 = loader.load(repeatIfRunning = false)
        assertNotSame(job1, job3, "New job if loading finished")
    }


    @Test
    fun `IF started with repeat and already running THEN extra action`() = runTest { job ->
        val loader = createLoader { delay(100L); throw RuntimeException() }

        val progressTracker = TestTracker()
        launch(job) { loader.progress.track(progressTracker) }

        var errorsCount = 0
        launch(job) { loader.error.collect { if (it != null) errorsCount++ } }

        // Launching regular refresh
        loader.load(repeatIfRunning = false)

        delay(50L)
        assertEquals(expected = 0, actual = errorsCount, "No error in the middle")
        assertEquals(TestTracker(1, 1), progressTracker, "Loading in the middle")

        // Forcing second refresh while the first one is in the middle
        loader.load(repeatIfRunning = true)

        // Forcing third refresh while the first one is in the middle
        loader.load(repeatIfRunning = true)

        delay(50L)
        assertEquals(expected = 0, actual = errorsCount, "No error in the end of first refresh")
        assertEquals(TestTracker(1, 1), progressTracker, "Loading in the end of first refresh")

        delay(100L)
        assertEquals(expected = 1, actual = errorsCount, "Error in the end of second refresh")
        assertEquals(TestTracker(1, 2), progressTracker, "Not loading in the end of second refresh")
    }


    private fun TestCoroutineScope.createLoader(action: suspend (LoadTracker) -> Unit) =
        Loader(logId, this, testDispatcher, action)

}
