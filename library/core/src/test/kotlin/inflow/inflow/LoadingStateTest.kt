package inflow.inflow

import inflow.BaseTest
import inflow.utils.TestTracker
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class LoadingStateTest : BaseTest() {

    @Test
    fun `Loading is triggered by refresh`() = runBlockingTestWithJob { job ->
        val tracker = TestTracker()
        val inflow = testInflow {}

        launch(job) {
            inflow.loading().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        inflow.refresh()

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `Loading is triggered by cache`() = runBlockingTestWithJob { job ->
        val tracker = TestTracker()
        val inflow = testInflow {}

        launch(job) {
            inflow.loading().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        // Briefly subscribing to trigger refresh
        launch(job) { inflow.data().take(1).collect() }

        assertEquals(TestTracker(1, 1), tracker, "Refresh is started but not finished")

        delay(100L)

        assertEquals(TestTracker(1, 2), tracker, "Refresh is finished")
    }

    @Test
    fun `Loading is not triggered by cache with no autoRefresh`() = runBlockingTestWithJob { job ->
        val tracker = TestTracker()
        val inflow = testInflow {}

        launch(job) {
            inflow.loading().track(tracker)
        }

        assertEquals(TestTracker(0, 1), tracker, "Not loading by default")

        launch(job) {
            inflow.data(autoRefresh = false).take(1).collect()
        }

        delay(1000L)

        assertEquals(TestTracker(0, 1), tracker, "Loading never started")
    }

}
