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
    fun `Cache is required`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cacheWriter {}
                loader {}
            }
        }
    }

    @Test
    fun `Cache writer is required`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow())
                loader {}
            }
        }
    }

    @Test
    fun `Cache timeout is not negative`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                cacheKeepSubscribedTimeout(-1L)
            }
        }
    }

    @Test
    fun `Loader is required`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            inflow {
                cache(emptyFlow<Unit>())
                cacheWriter {}
            }
        }
    }

    @Test
    fun `Retry time is positive`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            testInflow {
                loadRetryTime(0L)
            }
        }
    }

}
