package inflow.operators

import inflow.BaseTest
import inflow.ExpirationProvider
import inflow.ExpiresAt
import inflow.ExpiresIf
import inflow.internal.scheduleUpdates
import inflow.utils.TestTracker
import inflow.utils.now
import inflow.utils.runReal
import inflow.utils.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class ScheduleUpdatesTest : BaseTest() {

    @Test
    fun `IF not updated THEN one update and few retries`() = runTest { job ->
        var counter = 0
        launch(job) {
            scheduleWithDefaults(loader = { counter++ })
        }

        delay(300L)
        assertEquals(expected = 3, counter, "1 update and 2 retries")
    }

    @Test
    fun `IF new data is not expired THEN one update and no retries`() = runTest { job ->
        val cache = MutableStateFlow(0L)

        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                cache = cache,
                loader = {
                    counter++
                    cache.emit(Long.MAX_VALUE)
                }
            )
        }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 1, counter, "1 update and no retries")
    }

    @Test
    fun `IF cache is not expired THEN no update and no retries`() = runTest { job ->
        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                cache = MutableStateFlow(Long.MAX_VALUE),
                loader = { counter++ }
            )
        }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 0, counter, "No update and no retries")
    }

    @Test
    fun `IF cache is expiring THEN new updates are called`() = runReal {
        val cache = MutableStateFlow(0L)

        var counter = 0
        val job = launch {
            scheduleWithDefaults(
                cache = cache,
                loader = {
                    counter++
                    cache.emit(now() + 30L)
                }
            )
        }

        delay(75L) // Expecting loads after 0ms, 30ms and 60ms
        assertEquals(expected = 3, counter, "Several updates called")

        job.cancel()
    }

    @Test
    fun `IF using dynamic expiration that never expires THEN no updates`() = runTest { job ->
        var expired = false
        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                expiration = ExpiresIf(interval = 100L) { expired },
                loader = { counter++ }
            )
        }

        delay(1000L)
        assertEquals(expected = 0, counter, "No updates")

        expired = true
        delay(100L)
        assertEquals(expected = 1, counter, "Update is called once expired")
    }


    @Test
    fun `IF retry time is 0 THEN error`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            scheduleWithDefaults(retryTime = 0L)
        }
    }

    @Test
    fun `IF retry time is 'MAX_VALUE' THEN never retry`() = runTest { job ->
        var counter = 0
        launch(job) {
            scheduleWithDefaults(retryTime = Long.MAX_VALUE, loader = { counter++ })
        }

        delay(Long.MAX_VALUE - 1L)
        delay(Long.MAX_VALUE - 1L)

        assertEquals(expected = 1, actual = counter, "No retries are called")
    }

    @Test
    fun `IF slow loading is finished THEN retry is not called`() = runTest { job ->
        val cache = MutableStateFlow(0L)

        val tracker = TestTracker()
        launch(job) {
            scheduleWithDefaults(
                cache = cache,
                retryTime = 100L,
                loader = {
                    tracker.active++
                    delay(200L) // Note: bigger than retry time

                    cache.emit(Long.MAX_VALUE) // Should not trigger updates
                    tracker.idle++
                }
            )
        }

        delay(50L)
        // State: loading is started but not finished yet, retry is not called as well.
        assertEquals(TestTracker(1, 0), tracker, "First is loading and not finished")

        delay(100L)
        // State: loading is not finished yet, retry loading is not called yet.
        assertEquals(TestTracker(1, 0), tracker, "First is loading. No second loading")

        delay(100L)
        // State: loading is finished and newly loaded value should never expire,
        // no retry is called at this point even though loading time was bigger than retry time.
        assertEquals(TestTracker(1, 1), tracker, "First is finished. No second loading")
    }


    // Helper function to avoid defining same things in every test
    private suspend fun scheduleWithDefaults(
        cache: Flow<Long> = MutableStateFlow(0L),
        expiration: ExpirationProvider<Long> = ExpiresAt { it },
        retryTime: Long = 100L,
        loader: suspend () -> Unit = {}
    ) = scheduleUpdates(logId, cache, expiration, retryTime, loader)

}
