package inflow.inflow

import inflow.BaseTest
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadingStateTest : BaseTest() {

    @Test
    fun `IF refresh is called TEHN loading is triggered`() = runTest { job ->
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
    fun `IF data is subscribed with autoRefresh=true THEN loading is triggered`() =
        runTest { job ->
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
    fun `IF data is subscribed with autoRefresh=false THEN loading is not triggered`() =
        runTest { job ->
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
