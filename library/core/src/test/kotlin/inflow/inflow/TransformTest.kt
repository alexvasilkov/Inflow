@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.cache
import inflow.cached
import inflow.data
import inflow.map
import inflow.refresh
import inflow.refreshState
import inflow.utils.TestTracker
import inflow.utils.runTest
import inflow.utils.testInflow
import inflow.utils.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ExperimentalCoroutinesApi
class TransformTest : BaseTest() {

    @Test
    fun `IF mapped THEN same loading state`() = runTest { job ->
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val trackerOrig = TestTracker()
        val trackerMapped = TestTracker()

        launch(job) { inflow.refreshState().track(trackerOrig) }
        launch(job) { mapped.refreshState().track(trackerMapped) }

        mapped.refresh()
        assertEquals(trackerOrig, trackerMapped, "Mapped inflow has same loading state")
    }

    @Test
    fun `IF inflow is mapped THEN data is mapped`() = runTest {
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val item1 = mapped.data().first { it != null }
        assertEquals(inflow.cached()?.toString(), item1, "Mapped item is loaded")

        val item2 = mapped.cache().first { it != null }
        assertEquals(inflow.cached()?.toString(), item2, "Mapped item is loaded from cache")
    }

    @Test
    fun `IF mapped THEN can be refreshed with join`() = runTest {
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        mapped.refresh().join()
    }

    @Test
    fun `IF mapped THEN can be refreshed with await`() = runTest {
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val mappedItem = mapped.refresh().await()
        val item = inflow.cached()

        assertNotNull(item, "Orig item is loaded too")
        assertEquals(item.toString(), mappedItem, "Mapped item is loaded with await")
    }

}
