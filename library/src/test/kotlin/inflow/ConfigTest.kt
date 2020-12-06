package inflow

import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test

@ExperimentalCoroutinesApi
class ConfigTest : BaseTest() {

    @Test(expected = IllegalArgumentException::class)
    fun `Loader is required`() = runBlockingTest {
        testInflow {
            loader = null
        }
    }

    @Test
    fun `Cache and cache writer can be null together`() = runBlockingTest {
        testInflow {
            cache = null
            cacheWriter = null
        }
    }

    @Test
    fun `Cache and cache writer can be non-null`() = runBlockingTest {
        testInflow {}
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache writer should be null if cache is null`() = runBlockingTest {
        testInflow {
            cache = null
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Cache writer should be non-null if cache is non-null`() = runBlockingTest {
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
    fun `Retry time is positive`() = runBlockingTest {
        testInflow {
            loadRetryTime = 0L
        }
    }

}
