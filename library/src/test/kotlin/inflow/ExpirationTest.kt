package inflow

import inflow.utils.TestItem
import inflow.utils.now
import inflow.utils.runBlockingTestWithJob
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ExpirationTest : BaseTest() {

    @Test
    fun `IfNull -- One update and few retries for null value`() = runBlockingTestWithJob { job ->
        var counter = 0
        val inflow = testInflow {
            loader = {
                counter++
                throw RuntimeException()
            }
            cacheExpiration = ExpiresIn.IfNull()
        }

        launch(job) { inflow.data().collect() }

        delay(300L)
        assertEquals(expected = 3, counter, "1 update and 2 retries")
    }

    @Test(timeout = 1_000L)
    fun `IfNull -- One update and no retries for null value`() = runBlockingTestWithJob { job ->
        var counter = 0
        val inflow = testInflow {
            loader = {
                counter++
                TestItem(0L)
            }
            cacheExpiration = ExpiresIn.IfNull()
        }

        launch(job) { inflow.data().collect() }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 1, counter, "1 update and no retries")
    }

    @Test(timeout = 1_000L)
    fun `IfNull -- No update and no retries for non-null value`() = runBlockingTestWithJob { job ->
        var counter = 0
        val inflow = testInflow {
            cache = MutableStateFlow(TestItem(0L))
            loader = {
                counter++
                throw RuntimeException()
            }
            cacheExpiration = ExpiresIn.IfNull()
        }

        launch(job) { inflow.data().collect() }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 0, counter, "No update and no retries")
    }


    @Test(expected = IllegalArgumentException::class)
    fun `Duration -- Duration cannot be zero`() = runBlockingTest {
        ExpiresIn.Duration<TestItem?>(0L)
    }

    @Test
    fun `Duration -- Expiration updates are called`() = runBlockingTestWithJob { job ->
        var counter = 0
        val inflow = testInflow {
            loader = {
                counter++
                TestItem(now())
            }
            cacheExpiration = ExpiresIn.Duration(50L) { this?.loadedAt ?: 0L }
        }

        launch(job) { inflow.data().collect() }

        assertEquals(expected = 1, counter, "First update is called")

        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(55L) // Has to use blocking delay to have real time updated
        delay(55L) // Updating test time manually

        assertEquals(expected = 2, actual = counter, "Expiration update is called")
    }

    @Test
    fun `Duration -- 'LoadedAt' variant is identical`() = runBlockingTest {
        val strategy = ExpiresIn.Duration<TestItem?>(200L)

        // Nullable strategy
        assertEquals(expected = 0L, strategy.expiresIn(null), "Expired if null")

        // Expiration time, fuzzy check because of possible timing issues
        val expiresIn = strategy.expiresIn(TestItem(now() - 100L))
        assertTrue(expiresIn in 95..105, "Uses loadedAt")
    }


    @Test(expected = AssertionError::class)
    fun `Loader cannot return stale data -- real threading`(): Unit = runBlocking {
        // Using real threading along with setDefaultUncaughtExceptionHandler to receive errors
        // thrown inside coroutines. There is no other way to get internal errors without changing
        // Inflow API and allow setting custom coroutine context instead of just a dispatcher.
        var error: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, e -> error = e }

        val inflow = inflow<Unit?> {
            loader = { null }
            cacheExpiration = ExpiresIn.IfNull()
        }
        inflow.refresh()

        delay(25L)
        throw error!!
    }

}
