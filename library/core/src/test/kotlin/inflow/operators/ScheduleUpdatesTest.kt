package inflow.operators

import inflow.BaseTest
import inflow.internal.scheduleUpdates
import inflow.utils.runBlockingTestWithJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

@ExperimentalCoroutinesApi
class ScheduleUpdatesTest : BaseTest() {

    @Test
    fun `One update and few retries if cache is never updated`() = runBlockingTestWithJob { job ->
        var counter = 0
        launch(job) {
            scheduleWithDefaults(loader = { counter++ })
        }

        delay(300L)
        assertEquals(expected = 3, counter, "1 update and 2 retries")
    }

    @Test(timeout = 1_000L)
    fun `One update and no retries if new data is not expired`() = runBlockingTestWithJob { job ->
        val cacheExpiration = MutableStateFlow(0L)

        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                cacheExpiration = cacheExpiration,
                loader = {
                    counter++
                    cacheExpiration.emit(Long.MAX_VALUE)
                }
            )
        }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 1, counter, "1 update and no retries")
    }

    @Test
    fun `No update and no retries if cache is not expired`() = runBlockingTestWithJob { job ->
        val cacheExpiration = MutableStateFlow(Long.MAX_VALUE)

        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                cacheExpiration = cacheExpiration,
                loader = { counter++ }
            )
        }

        delay(Long.MAX_VALUE - 1L)
        assertEquals(expected = 0, counter, "No update and no retries")
    }

    @Test
    fun `Updates are called following cache expiration`() = runBlockingTestWithJob { job ->
        val cacheExpiration = MutableSharedFlow<Long>(replay = 1)
        cacheExpiration.emit(50L)

        var counter = 0
        launch(job) {
            scheduleWithDefaults(
                cacheExpiration = cacheExpiration,
                loader = {
                    counter++
                    cacheExpiration.emit(50L)
                }
            )
        }

        delay(4 * 50L + 1L) // Extra time to track last update
        assertEquals(expected = 4, counter, "Several updates called")
    }


    @Test(expected = IllegalArgumentException::class)
    fun `Retry time cannot be zero`() = runBlockingTest {
        scheduleWithDefaults(retryTime = 0L)
    }

    @Test(timeout = 1_000L)
    fun `Retry time can be set to never retry`() = runBlockingTestWithJob { job ->
        var counter = 0
        launch(job) {
            scheduleWithDefaults(retryTime = Long.MAX_VALUE, loader = { counter++ })
        }

        delay(Long.MAX_VALUE - 1L)
        delay(Long.MAX_VALUE - 1L)

        assertEquals(expected = 1, actual = counter, "No retries are called")
    }

    @Test(timeout = 1_000L)
    fun `Retry is not called if slow loading is successful`() = runBlockingTestWithJob { job ->
        val cacheExpiration = MutableStateFlow(0L)

        var counterStart = 0
        var counterEnd = 0
        launch(job) {
            scheduleWithDefaults(
                cacheExpiration = cacheExpiration,
                retryTime = 100L,
                loader = {
                    counterStart++
                    delay(190L) // Note: bigger than retry time

                    launch {
                        delay(10L) // Simulating cache delay
                        cacheExpiration.emit(Long.MAX_VALUE) // Should not trigger updates
                        counterEnd++
                    }
                }
            )
        }

        delay(50L)
        // State: loading is started but not finished yet, retry is not called as well.
        assertEquals(expected = 1, actual = counterStart, "First loading is started")
        assertEquals(expected = 0, actual = counterEnd, "First loading is not finished")

        delay(100L)
        // State: loading is not finished yet, retry loading is not called yet.
        assertEquals(expected = 1, actual = counterStart, "No second loading started")
        assertEquals(expected = 0, actual = counterEnd, "First loading is not finished")

        delay(100L)
        // State: loading is finished and newly loaded value should never expire,
        // no retry is called at this point even though loading time was bigger than retry time.
        assertEquals(expected = 1, actual = counterStart, "No second loading started")
        assertEquals(expected = 1, actual = counterEnd, "First loading finished")
    }


    // Helper function to avoid defining same things in every test
    private suspend fun scheduleWithDefaults(
        cacheExpiration: Flow<Long> = MutableStateFlow(0L),
        retryTime: Long = 100L,
        loader: suspend () -> Unit = {}
    ) = scheduleUpdates(logId, cacheExpiration, retryTime, loader)

}