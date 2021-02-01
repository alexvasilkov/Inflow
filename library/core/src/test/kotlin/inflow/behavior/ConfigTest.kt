@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.base.BaseTest
import inflow.base.runTest
import inflow.base.testInflow
import inflow.inflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test
    fun `IF no data THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {}
        }
    }

    @Test
    fun `IF data set twice THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {
                data(flowOf(Unit)) {}
                data(flowOf(Unit)) {}
            }
        }
    }

    @Test
    fun `IF cache timeout is negative THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                keepCacheSubscribedTimeout(-1L)
            }
        }
    }

    @Test
    fun `IF retry time is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                retryTime(0L)
            }
        }
    }

}
