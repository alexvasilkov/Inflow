package inflow.inflow

import inflow.BaseTest
import inflow.inflow
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test(expected = IllegalArgumentException::class)
    fun `Cache is required`() = runBlockingTest {
        inflow {
            cacheWriter {}
            loader {}
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache writer is required`() = runBlockingTest {
        inflow {
            cache(emptyFlow())
            loader {}
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache timeout is not negative`() = runBlockingTest {
        testInflow {
            cacheKeepSubscribedTimeout(-1L)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Loader is required`() = runBlockingTest {
        inflow {
            cache(emptyFlow<Unit>())
            cacheWriter {}
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Retry time is positive`() = runBlockingTest {
        testInflow {
            loadRetryTime(0L)
        }
    }

}
