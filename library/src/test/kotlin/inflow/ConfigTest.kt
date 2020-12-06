package inflow

import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test(expected = IllegalArgumentException::class)
    fun `Cache is required`() = runBlockingTest {
        testInflow {
            cache = null
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache writer is required`() = runBlockingTest {
        testInflow {
            cacheWriter = null
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache timeout is not negative`() = runBlockingTest {
        testInflow {
            cacheKeepSubscribedTimeout = -1L
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Loader is required`() = runBlockingTest {
        testInflow {
            loader = null
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Retry time is positive`() = runBlockingTest {
        testInflow {
            loadRetryTime = 0L
        }
    }

}
