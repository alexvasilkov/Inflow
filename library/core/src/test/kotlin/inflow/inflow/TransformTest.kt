package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.latest
import inflow.map
import inflow.utils.TestItem
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class TransformTest : BaseTest() {

    @Test
    fun `IF mapped THEN same loading state`() = runTest { job ->
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
    fun `IF mapped THEN same error state`() = runTest {
        val inflow: Inflow<TestItem?> = testInflow {
            loader { throw RuntimeException() }
        }
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        mapped.refresh()
        assertSame(inflow.error().value, mapped.error().value, "Mapped inflow has same error")
    }

    @Test
    fun `IF mapped THEN mapped data`() = runTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val item1 = mapped.data(autoRefresh = true).first { it != null }
        assertEquals(inflow.latest()?.loadedAt, item1, "Mapped item is loaded")

        val item2 = mapped.data(autoRefresh = false).first { it != null }
        assertEquals(inflow.latest()?.loadedAt, item2, "Mapped item is loaded from cache")
    }

    @Test
    fun `IF mapped THEN mapped data flows are cached`() = runTest {
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
    fun `IF mapped THEN can be refreshed with join`() = runTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        mapped.refresh(repeatIfRunning = false).join()
    }

    @Test
    fun `IF mapped THEN can be refreshed with await`() = runTest {
        val inflow: Inflow<TestItem?> = testInflow {}
        val mapped: Inflow<Long?> = inflow.map { it?.loadedAt }

        val mappedItem = mapped.refresh(repeatIfRunning = false).await()
        val item = inflow.latest()

        assertNotNull(item, "Orig item is loaded too")
        assertEquals(item.loadedAt, mappedItem, "Mapped item is loaded with await")
    }

}
