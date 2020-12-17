package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.inflow
import inflow.utils.TestItem
import inflow.utils.assertCrash
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ExpirationTest : BaseTest() {

    @Test
    fun `ExpiresIfNull -- One update and few retries for null value`() =
        runBlockingTestWithJob { job ->
            var counter = 0
            val inflow = testInflow {
                loader {
                    counter++
                    throw RuntimeException()
                }
                cacheExpiration(ExpiresIfNull())
            }

            launch(job) { inflow.data().collect() }

            delay(300L)
            assertEquals(expected = 3, counter, "1 update and 2 retries")
        }

    @Test
    fun `ExpiresIfNull -- One update and no retries for null value`() =
        runBlockingTestWithJob { job ->
            var counter = 0
            val inflow = testInflow {
                loader {
                    counter++
                    TestItem(0L)
                }
                cacheExpiration(ExpiresIfNull())
            }

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 1, counter, "1 update and no retries")
        }

    @Test
    fun `ExpiresIfNull -- No update and no retries for non-null value`() =
        runBlockingTestWithJob { job ->
            var counter = 0
            val inflow = testInflow {
                cache(MutableStateFlow(TestItem(0L)))
                loader {
                    counter++
                    throw RuntimeException()
                }
                cacheExpiration(ExpiresIfNull())
            }

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 0, counter, "No update and no retries")
        }


    @Test
    fun `ExpiresIn -- Duration cannot be zero`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            ExpiresIn<TestItem?>(0L)
        }
    }

    @Test
    fun `ExpiresIn -- Expiration updates are called`() = runBlockingTestWithJob { job ->
        var counter = 0
        val inflow = testInflow {
            loader {
                counter++
                TestItem(now())
            }
            cacheExpiration(ExpiresIn(duration = 50L, loadedAt = { this?.loadedAt ?: 0L }))
        }

        launch(job) { inflow.data().collect() }

        assertEquals(expected = 1, counter, "First update is called")

        @Suppress("BlockingMethodInNonBlockingContext")
        Thread.sleep(55L) // Has to use blocking delay to have real time updated
        delay(55L) // Updating test time manually

        assertEquals(expected = 2, actual = counter, "Expiration update is called")
    }

    @Test
    fun `ExpiresIn -- 'LoadedAt' variant uses loadedAt value`() = runBlockingTest {
        val strategy = ExpiresIn<TestItem?>(200L)

        // Nullable strategy
        assertEquals(expected = 0L, strategy.expiresIn(null), "Expired if null")

        // Expiration time, fuzzy check because of possible timing issues
        val expiresIn = strategy.expiresIn(TestItem(now() - 100L))
        assertTrue(expiresIn in 95..105, "Uses loadedAt value")
    }


    @Test
    fun `Loader cannot return stale data -- real threading`(): Unit = runBlocking {
        assertCrash<AssertionError> {
            val inflow = inflow<Unit?> {
                cacheInMemory(null)
                loader { null }
                cacheExpiration(ExpiresIfNull())
            }
            inflow.refresh()
        }
    }

}
