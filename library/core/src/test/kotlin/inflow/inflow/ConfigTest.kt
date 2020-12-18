package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runBlockingTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test
    fun `IF no cache THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cacheWriter {}
                loader {}
            }
        }
    }

    @Test
    fun `IF no cache writer THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow())
                loader {}
            }
        }
    }

    @Test
    fun `IF cache timeout is negative THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                cacheKeepSubscribedTimeout(-1L)
            }
        }
    }

    @Test
    fun `IF no loader THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow<Unit>())
                cacheWriter {}
            }
        }
    }

    @Test
    fun `IF retry time is 0 THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                loadRetryTime(0L)
            }
        }
    }

}
