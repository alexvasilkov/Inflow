package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.latest
import inflow.map
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
        val inflow: Inflow<Int?> = testInflow {}
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val trackerOrig = TestTracker()
        val trackerMapped = TestTracker()

        launch(job) { inflow.loading().track(trackerOrig) }
        launch(job) { mapped.loading().track(trackerMapped) }

        mapped.refresh()
        assertEquals(trackerOrig, trackerMapped, "Mapped inflow has same loading state")
    }

    @Test
    fun `IF mapped THEN same error state`() = runTest {
        val inflow: Inflow<Int?> = testInflow {
            loader { throw RuntimeException() }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        mapped.refresh()
        assertSame(inflow.error().value, mapped.error().value, "Mapped inflow has same error")
    }

    @Test
    fun `IF mapped THEN mapped data`() = runTest {
        val inflow: Inflow<Int?> = testInflow {}
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val item1 = mapped.data(autoRefresh = true).first { it != null }
        assertEquals(inflow.latest()?.toString(), item1, "Mapped item is loaded")

        val item2 = mapped.data(autoRefresh = false).first { it != null }
        assertEquals(inflow.latest()?.toString(), item2, "Mapped item is loaded from cache")
    }

    @Test
    fun `IF mapped THEN mapped data flows are cached`() = runTest {
        val inflow: Inflow<Int?> = testInflow {}
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

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
        val inflow: Inflow<Int?> = testInflow {}
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        mapped.refresh(repeatIfRunning = false).join()
    }

    @Test
    fun `IF mapped THEN can be refreshed with await`() = runTest {
        val inflow: Inflow<Int?> = testInflow {}
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val mappedItem = mapped.refresh(repeatIfRunning = false).await()
        val item = inflow.latest()

        assertNotNull(item, "Orig item is loaded too")
        assertEquals(item.toString(), mappedItem, "Mapped item is loaded with await")
    }

}
