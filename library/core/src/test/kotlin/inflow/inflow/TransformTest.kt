package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.latest
import inflow.map
import inflow.utils.TestItem
import inflow.utils.TestTracker
import inflow.utils.runTestWithJob
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

@ExperimentalCoroutinesApi
class TransformTest : BaseTest() {

    @Test
    fun `IF mapped THEN same loading state`() = runTestWithJob { job ->
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val trackerOrig = TestTracker()
        val trackerMapped = TestTracker()

        launch(job) { inflow.loading().track(trackerOrig) }
        launch(job) { mapped.loading().track(trackerMapped) }

        mapped.refresh()
        assertEquals(trackerOrig, trackerMapped, "Mapped inflow has same loading state")
    }

    @Test
    fun `IF mapped THEN same error state`() = runBlockingTest {
        val inflow: Inflow<TestItem?> = testInflow {
            loader { throw RuntimeException() }
        }
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        mapped.refresh()
        assertSame(inflow.error().value, mapped.error().value, "Mapped inflow has same error")
    }

    @Test
    fun `IF mapped THEN mapped data`() = runBlockingTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val item1 = mapped.data(autoRefresh = true).first { it != null }
        assertEquals(expected = currentTime, item1, "Mapped item is loaded")

        val item2 = mapped.data(autoRefresh = false).first { it != null }
        assertEquals(expected = currentTime, item2, "Mapped item is loaded from cache")
    }

    @Test
    fun `IF mapped THEN mapped data flows are cached`() = runBlockingTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        assertSame(
            mapped.data(autoRefresh = false),
            mapped.data(autoRefresh = false),
            "Cached flow is re-used"
        )

        assertSame(
            mapped.data(autoRefresh = true),
            mapped.data(autoRefresh = true),
            "Auto flow is re-used"
        )
    }

    @Test
    fun `IF mapped THEN can be refreshed with join`() = runBlockingTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        mapped.refresh(repeatIfRunning = false).join()
    }

    @Test
    fun `IF mapped THEN can be refreshed with await`() = runBlockingTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val mappedItem = mapped.refresh(repeatIfRunning = false).await()
        val item = inflow.latest()

        assertEquals(expected = currentTime, mappedItem, "Mapped item is loaded with await")
        assertEquals(expected = currentTime, item?.loadedAt, "Orig item is loaded too")
    }

}
