package inflow.inflow

import inflow.BaseTest
import inflow.Inflow
import inflow.cache
import inflow.cached
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
    fun `IF mapped THEN same progress state`() = runTest { job ->
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { 0 }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        val trackerOrig = TestTracker()
        val trackerMapped = TestTracker()

        launch(job) { inflow.progress().track(trackerOrig) }
        launch(job) { mapped.progress().track(trackerMapped) }

        mapped.refresh()
        assertEquals(trackerOrig, trackerMapped, "Mapped inflow has same loading state")
    }

    @Test
    fun `IF mapped THEN same error state`() = runTest {
        val inflow: Inflow<Int?> = testInflow {
            data(initial = null) { throw RuntimeException() }
        }
        val mapped: Inflow<String?> = inflow.map { it?.toString() }

        mapped.refresh()
        assertSame(inflow.error().first(), mapped.error().first(), "Mapped inflow has same error")
    }

    @Test
    fun `IF mapped THEN mapped data`() = runTest {
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
