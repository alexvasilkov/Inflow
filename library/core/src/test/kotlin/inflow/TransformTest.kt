package inflow

import inflow.utils.TestItem
import inflow.utils.TestTracker
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class TransformTest : BaseTest() {

    @Test
    fun `Can map inflow`() = runBlockingTestWithJob { job ->
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val trackerOrig = TestTracker()
        val trackerMapped = TestTracker()

        launch(job) { inflow.loading().track(trackerOrig) }
        launch(job) { mapped.loading().track(trackerMapped) }

        val item1 = mapped.data(autoRefresh = true).first { it != null }
        assertEquals(expected = currentTime, item1, "Mapped item is loaded")

        val item2 = mapped.data(autoRefresh = false).first { it != null }
        assertEquals(expected = currentTime, item2, "Mapped item is loaded from cache")

        assertEquals(trackerOrig, trackerMapped, "Mapped inflow has same loading state")
    }

}
