@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.inflow

import inflow.BaseTest
import inflow.Expires
import inflow.cached
import inflow.data
import inflow.inflow
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runTest
import inflow.utils.testInflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class ExpirationTest : BaseTest() {

    @Test
    fun `IF Expires_never THEN no updates`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = null) { counter++; throw RuntimeException() }
                retryTime(100L)
                expiration(Expires.never())
            }

            launch(job) { inflow.data().collect() }

            delay(300L)
            assertEquals(expected = 0, counter, "0 updates")
        }

    @Test
    fun `IF Expires_ifNull AND cache is null AND cannot load THEN update and few retries`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = null) { counter++; throw RuntimeException() }
                retryTime(100L)
                expiration(Expires.ifNull())
            }

            launch(job) { inflow.data().collect() }

            delay(300L)
            assertEquals(expected = 3, counter, "1 update and 2 retries")
        }

    @Test
    fun `IF Expires_ifNull AND cache is null AND can load THEN update and no retries`() =
        runTest { job ->
            val inflow = testInflow {
                var count = 0
                data(initial = null) { delay(100L); count++ }
                expiration(Expires.ifNull())
            }

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 0, inflow.cached(), "1 update and no retries")
        }

    @Test
    fun `IF Expires_ifNull AND cache is not null THEN no update and no retries`() =
        runTest { job ->
            var counter = 0
            val inflow = testInflow {
                data(initial = 0) { counter++; throw RuntimeException() }
                expiration(Expires.ifNull())
            }

            launch(job) { inflow.data().collect() }

            delay(Long.MAX_VALUE - 1L)
            assertEquals(expected = 0, counter, "No update and no retries")
        }

    @Test
    fun `IF Expires_at AND expiresAt is MAX_VALUE THEN never expires`() = runTest {
        val expiration = Expires.at<Int> { Long.MAX_VALUE }
        val expiresIn = expiration.expiresIn(0)
        assertEquals(expected = Long.MAX_VALUE, actual = expiresIn, "Never expires")
    }

    @Test
    fun `IF Expires_at AND cache is expiring THEN updates are called`() {
        testExpiration(Expires.at { it + 30L })
    }

    @Test
    fun `IF Expires_after AND cache is expiring THEN updates are called`() {
        testExpiration(Expires.after(30L) { it })
    }

    private fun testExpiration(expiration: Expires<Long>) = runReal {
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
    fun `IF Expires_after AND duration is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            Expires.after<Int>(duration = 0L) { 1L }
        }
    }

    @Test
    fun `IF Expires_after AND duration is MAX_VALUE THEN never expires`() = runTest {
        val expiration = Expires.after<Int>(Long.MAX_VALUE) { 1L }
        val expiresIn = expiration.expiresIn(0)
        assertEquals(expected = Long.MAX_VALUE, actual = expiresIn, "Never expires")
    }

    @Test
    fun `IF Expires_after AND loadedAt is MAX_VALUE THEN never expires`() = runTest {
        val expiration = Expires.after<Int>(1L) { Long.MAX_VALUE }
        val expiresIn = expiration.expiresIn(0)
        assertEquals(expected = Long.MAX_VALUE, actual = expiresIn, "Never expires")
    }

}
