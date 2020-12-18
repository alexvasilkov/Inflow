package inflow.inflow

import inflow.BaseTest
import inflow.ExpirationProvider
import inflow.ExpiresAt
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.inflow
import inflow.latest
import inflow.utils.assertCrash
import inflow.utils.now
import inflow.utils.runTest
import inflow.utils.runThreads
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpirationTest : BaseTest() {

    @Test
    fun `IF ExpiresIfNull AND cache is null AND cannot load THEN update and few retries`() =
        runTest { job ->
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
    fun `IF ExpiresIfNull AND cache is null AND can load THEN update and no retries`() =
        runTest { job ->
            val inflow = testInflow {}

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 0, inflow.latest(), "1 update and no retries")
        }

    @Test
    fun `IF ExpiresIfNull AND cache is not null THEN no update and no retries`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                cacheInMemory(0)
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
    fun `IF ExpiresAt AND cache is expiring THEN updates are called`() {
        testExpiration(ExpiresAt { it + 30L })
    }

    @Test
    fun `IF ExpiresIn AND cache is expiring THEN updates are called`() {
        testExpiration(ExpiresIn(30L) { it })
    }

    private fun testExpiration(expiration: ExpirationProvider<Long>) = runThreads {
        var counter = 0
        val inflow = inflow {
            cacheInMemory(0L)
            loader {
                counter++
                now()
            }
            cacheExpiration(expiration)
        }

        val collectJob = launch { inflow.data().collect() }

        delay(15L)
        assertEquals(expected = 1, counter, "First update is called")

        delay(25L)
        assertEquals(expected = 2, actual = counter, "Expiration update is called")

        collectJob.cancel()
    }

    @Test
    fun `IF ExpiresIn AND duration is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ExpiresIn<Int>(0L) { 0L }
        }
    }


    @Test
    fun `IF loader returns expired data THEN crash`() = runThreads {
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
