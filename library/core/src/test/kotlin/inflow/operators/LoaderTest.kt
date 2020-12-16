package inflow.operators

import inflow.BaseTest
import inflow.internal.Loader
import inflow.utils.TestTracker
import inflow.utils.runBlockingTestWithJob
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class LoaderTest : BaseTest() {

    @Test
    fun `Loading state is propagated`() = runBlockingTest {
        val loader = Loader(logId, this) { delay(100L) }
        loader.load(repeatIfRunning = false)

        assertTrue(loader.loading.value, "In loading state")
        delay(100L)
        assertFalse(loader.loading.value, "Loading finished")
    }

    @Test
    fun `Error is propagated`() = runBlockingTest {
        val exception = RuntimeException()
        val loader = Loader(logId, this) {
            delay(100L)
            throw exception
        }
        loader.load(repeatIfRunning = false)

        assertNull(loader.error.value, "No error in the beginning")
        delay(100L)
        assertSame(expected = exception, actual = loader.error.value, "Error is sent")
    }

    @Test
    fun `Error is cleared on start`() = runBlockingTest {
        val loader = Loader(logId, this) {
            delay(100L)
            throw RuntimeException()
        }

        loader.load(repeatIfRunning = false)
        delay(100L)
        assertNotNull(loader.error.value, "Error is detected")

        loader.load(repeatIfRunning = false)
        assertNull(loader.error.value, "Error is cleared on start")
    }


    @Test
    fun `Only one action can run at a time`() = runBlockingTest {
        val loader = Loader(logId, this) { delay(100L) }

        val job1 = loader.load(repeatIfRunning = false)

        delay(50L)
        val job2 = loader.load(repeatIfRunning = false)
        assertSame(job1, job2, "Same job if still running")

        delay(50L)
        val job3 = loader.load(repeatIfRunning = false)
        assertNotSame(job1, job3, "New job if loading finished")
    }


    @Test
    fun `Can force extra refresh if already running`() = runBlockingTestWithJob { job ->
        val loader = Loader(logId, this) {
            delay(100L)
            throw RuntimeException()
        }

        val loadingsTracker = TestTracker()
        launch(job) { loader.loading.track(loadingsTracker) }

        var errorsCount = 0
        launch(job) { loader.error.collect { if (it != null) errorsCount++ } }

        // Launching regular refresh
        loader.load(repeatIfRunning = false)

        delay(50L)
        assertEquals(expected = 0, actual = errorsCount, "No error in the middle")
        assertEquals(TestTracker(1, 1), loadingsTracker, "Loading in the middle")

        // Forcing second refresh while the first one is in the middle
        loader.load(repeatIfRunning = true)

        // Forcing third refresh while the first one is in the middle
        loader.load(repeatIfRunning = true)

        delay(50L)
        assertEquals(expected = 0, actual = errorsCount, "No error in the end of first refresh")
        assertEquals(TestTracker(1, 1), loadingsTracker, "Loading in the end of first refresh")

        delay(100L)
        assertEquals(expected = 1, actual = errorsCount, "Error in the end of second refresh")
        assertEquals(TestTracker(1, 2), loadingsTracker, "Not loading in the end of second refresh")
    }

}