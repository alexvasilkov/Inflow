@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.MemoryCache
import inflow.base.BaseTest
import inflow.base.runTest
import inflow.base.testInflow
import inflow.data
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
class DataTest : BaseTest() {

    @Test
    fun `IF expired data is subscribed THEN refresh is triggered`() = runTest { job ->
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
        }

        var item: Int? = Int.MIN_VALUE
        launch(job) { inflow.data().collect { item = it } }

        assertNull(item, "Not item in the beginning")

        delay(100L)
        assertEquals(expected = 0, actual = item, "Fresh item is loaded automatically")
    }

    @Test
    fun `IF expired cache is emitted during loading THEN no extra refresh`() = runTest { job ->
        val cache = MemoryCache.create<Int?>(-1)
        val inflow = testInflow {
            var count = 0
            data(cache) { delay(100L); count++ }
            expiration { if (it == null || it < 0) 0L else Long.MAX_VALUE }
        }

        var item: Int? = Int.MIN_VALUE
        launch(job) { inflow.data().collect { item = it } }

        assertEquals(expected = -1, actual = item, "Initial item from cache")

        // Sending expired item again while loading is in place
        delay(50L)
        cache.write(-2)
        assertEquals(expected = -2, actual = item, "Cache changes are available immediately")

        delay(50L)
        assertEquals(expected = 0, actual = item, "Fresh item is loaded automatically")

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 0, actual = item, "No extra item is loaded")
    }

}
