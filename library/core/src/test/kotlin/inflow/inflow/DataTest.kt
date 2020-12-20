package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIf
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataTest : BaseTest() {

    @Test
    fun `IF expired data is subscribed THEN refresh is triggered`() = runTest { job ->
        val inflow = testInflow {}

        var item: Int? = Int.MIN_VALUE
        launch(job) { inflow.data().collect { item = it } }

        assertNull(item, "Not item in the beginning")

        delay(100L)
        assertEquals(expected = 0, actual = item, "Fresh item is loaded automatically")
    }

    @Test
    fun `IF expired cache is emitted during loading THEN no extra refresh`() = runTest { job ->
        lateinit var writer: suspend (Int?) -> Unit
        val inflow = testInflow {
            cacheInMemory(-1)
            writer = cacheWriter!!
            cacheExpiration(ExpiresIf(Long.MAX_VALUE) { it == null || it < 0 })
        }

        var item: Int? = Int.MIN_VALUE
        launch(job) { inflow.data().collect { item = it } }

        assertEquals(expected = -1, actual = item, "Initial item from cache")

        // Sending expired item again while loading is in place
        delay(50L)
        writer(-2)

        delay(50L)
        assertEquals(expected = 0, actual = item, "Fresh item is loaded automatically")

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 0, actual = item, "No extra item is loaded")
    }

}
