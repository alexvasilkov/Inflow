package inflow.inflow

import inflow.BaseTest
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.inflow
import inflow.utils.TestItem
import inflow.utils.assertCrash
import inflow.utils.now
import inflow.utils.runTestWithJob
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
    fun `(ExpiresIfNull) IF cache is null and cannot load THEN update and few retries`() =
        runTestWithJob { job ->
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
    fun `(ExpiresIfNull) IF cache is null and can load THEN update and no retries`() =
        runTestWithJob { job ->
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
    fun `(ExpiresIfNull) IF cache is not null THEN no update and no retries`() =
        runTestWithJob { job ->
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
    fun `(ExpiresIn) IF duration is 0 THEN error`() = runBlockingTest {
        assertFailsWith<IllegalArgumentException> {
            ExpiresIn<TestItem?>(0L)
        }
    }

    @Test
    fun `(ExpiresIn) IF cache is expiring THEN updates are called`() = runTestWithJob { job ->
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
    fun `(ExpiresIn) IF 'LoadedAt' provided THEN loadedAt value is used`() = runBlockingTest {
        val strategy = ExpiresIn<TestItem?>(200L)

        // Nullable strategy
        assertEquals(expected = 0L, strategy.expiresIn(null), "Expired if null")

        // Expiration time, fuzzy check because of possible timing issues
        val expiresIn = strategy.expiresIn(TestItem(now() - 100L))
        assertTrue(expiresIn in 95..105, "Uses loadedAt value")
    }


    @Test
    fun `IF loader returns stale data THEN crash`(): Unit = runBlocking {
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
