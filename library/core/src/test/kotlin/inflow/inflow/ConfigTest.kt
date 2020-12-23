package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConfigTest : BaseTest() {

    @Test
    fun `IF no data THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow<Unit> {}
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
