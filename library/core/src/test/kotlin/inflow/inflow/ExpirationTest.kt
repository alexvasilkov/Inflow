package inflow.inflow

import inflow.BaseTest
import inflow.ExpirationProvider
import inflow.ExpiresAt
import inflow.ExpiresIfNull
import inflow.ExpiresIn
import inflow.ExpiresNever
import inflow.cached
import inflow.inflow
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpirationTest : BaseTest() {

    @Test
    fun `IF ExpiresNever THEN no updates`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = null) { counter++; throw RuntimeException() }
                retryTime(100L)
                expiration(ExpiresNever())
            }

            launch(job) { inflow.data().collect() }

            delay(300L)
            assertEquals(expected = 0, counter, "0 updates")
        }

    @Test
    fun `IF ExpiresIfNull AND cache is null AND cannot load THEN update and few retries`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = null) { counter++; throw RuntimeException() }
                retryTime(100L)
                expiration(ExpiresIfNull())
            }

            launch(job) { inflow.data().collect() }

            delay(300L)
            assertEquals(expected = 3, counter, "1 update and 2 retries")
        }

    @Test
    fun `IF ExpiresIfNull AND cache is null AND can load THEN update and no retries`() =
        runTest { job ->
            val inflow = testInflow {
                var count = 0
                data(initial = null) { delay(100L); count++ }
            }

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 0, inflow.cached(), "1 update and no retries")
        }

    @Test
    fun `IF ExpiresIfNull AND cache is not null THEN no update and no retries`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = 0) { counter++; throw RuntimeException() }
                expiration(ExpiresIfNull())
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

    private fun testExpiration(expiration: ExpirationProvider<Long>) = runReal {
        var counter = 0
        val inflow = inflow<Long> {
            // In both test cases initial expiresIn will be 0L
            data(initial = -30L) { counter++; now() }
            expiration(expiration)
        }

        val collectJob = launch { inflow.data().collect() }

        delay(15L)
        assertEquals(expected = 1, counter, "First update is called")

        delay(40L)
        assertEquals(expected = 2, actual = counter, "Expiration update is called")

        collectJob.cancel()
    }

    @Test
    fun `IF ExpiresIn AND duration is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            ExpiresIn<Int>(duration = 0L) { 1L }
        }
    }

    @Test
    fun `IF ExpiresAt AND expiresAt is MAX_VALUE THEN never expires`() = runTest {
        val expiration = ExpiresAt<Int> { Long.MAX_VALUE }
        val expiresIn = expiration.expiresIn(0)
        assertEquals(expected = Long.MAX_VALUE, actual = expiresIn, "Never expires")
    }

    @Test
    fun `IF ExpiresIn AND duration is MAX_VALUE THEN never expires`() = runTest {
        val expiration = ExpiresIn<Int>(Long.MAX_VALUE) { 1L }
        val expiresIn = expiration.expiresIn(0)
        assertEquals(expected = Long.MAX_VALUE, actual = expiresIn, "Never expires")
    }

}
