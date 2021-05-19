@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.base.BaseTest
import inflow.inflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test
    fun `IF no data THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {}
        }
    }

    @Test
    fun `IF cache timeout is negative THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {
                keepCacheSubscribedTimeout(-1L)
            }
        }
    }

    @Test
    fun `IF retry time is 0 THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {
                retryTime(0L)
            }
        }
    }

}
