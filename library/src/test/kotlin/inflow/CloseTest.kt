package inflow

import inflow.impl.InflowImpl
import inflow.utils.TestItem
import inflow.utils.TestTracker
import inflow.utils.lastLogMessage
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import java.lang.ref.WeakReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class CloseTest : BaseTest() {

    @Test(expected = IllegalStateException::class)
    fun `Cannot collect data if closed`() = runBlockingTest {
        val inflow = testInflow {}
        inflow.close()
        inflow.data()
    }

    @Test(expected = IllegalStateException::class)
    fun `Cannot collect loading state if closed`() = runBlockingTest {
        val inflow = testInflow {}
        inflow.close()
        inflow.loading()
    }

    @Test(expected = IllegalStateException::class)
    fun `Cannot collect errors if closed`() = runBlockingTest {
        val inflow = testInflow {}
        inflow.close()
        inflow.error()
    }

    @Test(expected = IllegalStateException::class)
    fun `Cannot refresh if closed`() = runBlockingTest {
        val inflow = testInflow {}
        inflow.close()
        inflow.refresh()
    }

    @Test(expected = IllegalStateException::class)
    fun `Cannot refresh blocking if closed`() = runBlockingTest {
        val inflow = testInflow {}
        inflow.close()
        inflow.refreshBlocking()
    }


    @Test
    fun `Data flow is stopped when inflow is closed`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {}

        var data: TestItem? = TestItem(Long.MIN_VALUE)
        launch(job) {
            inflow.data().collect { data = it }
        }

        assertNull(data, "No data in the beginning")

        delay(50L)
        inflow.close() // Closing in the middle of refresh

        delay(1000L)
        assertNull(data, "No data in the end")
    }

    @Test(timeout = 1_000L)
    fun `Loading state flow is stopped when inflow is closed`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {}

        val tracker = TestTracker()
        launch(job) {
            inflow.loading().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading in the beginning")

        inflow.refresh()
        assertEquals(TestTracker(1, 1), tracker, "Started loading")

        delay(50L)
        inflow.close() // Closing in the middle of refresh

        delay(1000L)
        assertEquals(TestTracker(1, 1), tracker, "No extra loading events once closed")
    }

    @Test
    fun `Errors flow is stopped when inflow is closed`() = runBlockingTestWithJob { job ->
        val inflow = testInflow {
            loader {
                delay(100L)
                throw RuntimeException()
            }
        }

        var error: Throwable? = IllegalStateException()
        launch(job) {
            inflow.error().collect { error = it }
        }

        inflow.refresh()

        delay(50L)
        inflow.close() // Closing in the middle of refresh

        delay(1000L)
        assertNull(error, "No error in the end")
    }

    @Test
    fun `Close during blocking refresh`() = runBlockingTest {
        val inflow = testInflow {}

        val job = launch {
            inflow.refreshBlocking()
        }

        // Closing in the middle of blocking refresh
        delay(50L)
        inflow.close()
        delay(50L)

        assertTrue(job.isCompleted, "Blocking refresh is completed")
    }

    @Test
    fun `Close with scope`() = runBlockingTest {
        val inflow = testInflow {} as InflowImpl

        val scope = CoroutineScope(coroutineContext + Job())
        inflow.closeWithScope(scope)

        assertFalse(inflow.closed, "Inflow is not closed")
        scope.cancel()
        assertTrue(inflow.closed, "Inflow is closed")
    }


    // This can be a pretty non-deterministic test, consider to remove it if it will fail
    @Test(timeout = 3_000L)
    fun `Finalize block is called to cancel inflow`(): Unit = runBlocking(Dispatchers.IO) {
        val ref = startInflowAsReference()

        while (ref.get() != null) {
            System.gc() // Forcing garbage-collection, hoping that it will collect unused inflow
            delay(100L)
        }

        assertEquals(lastLogMessage, "Inflow is garbage-collected", "Inflow was actually closed")
    }

    private fun startInflowAsReference(): WeakReference<Inflow<TestItem>> {
        val inflow = inflow {
            cacheInMemory(TestItem(0L))
            loader {
                @Suppress("BlockingMethodInNonBlockingContext")
                Thread.sleep(100L)
                throw RuntimeException()
            }
        }

        inflow.refresh()

        return WeakReference(inflow)
    }

}
