package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ConfigTest : BaseTest() {

    @Test
    fun `IF no cache THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cacheWriter {}
                loader {}
            }
        }
    }

    @Test
    fun `IF no cache writer THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow())
                loader {}
            }
        }
    }

    @Test
    fun `IF cache timeout is negative THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                cacheKeepSubscribedTimeout(-1L)
            }
        }
    }

    @Test
    fun `IF no loader THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow<Unit>())
                cacheWriter {}
            }
        }
    }

    @Test
    fun `IF retry time is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                loadRetryTime(0L)
            }
        }
    }

}
