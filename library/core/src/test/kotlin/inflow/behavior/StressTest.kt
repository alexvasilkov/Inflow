@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.State.Idle
import inflow.base.AtomicInt
import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.cache
import inflow.data
import inflow.inflow
import inflow.refresh
import inflow.refreshForced
import inflow.refreshState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class StressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF observe several inflows THEN no deadlocks`() = runReal {
        runStressTest { i ->
            for (j in 0 until 2) {
                val inflow = inflow<Unit?> {
                    logId("$i/$j")
                    data(initial = null) { delay(50L); throw RuntimeException() }
                    retryTime(Long.MAX_VALUE)
                }

                // Subscribing to start auto refresh
                val job = launch { inflow.data().collect() }

                // Waiting for error
                inflow.refreshState().first { it is Idle.Error } as Idle.Error
                job.cancel()
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF refresh several inflows THEN no deadlocks`() = runReal {
        runStressTest { i ->
            for (j in 0 until 2) {
                val inflow = inflow<Unit?> {
                    logId("$i/$j")
                    val memory = MutableSharedFlow<Unit?>(replay = 1).apply { tryEmit(null) }
                    data(
                        cache = memory,
                        // Delaying memory cache writer
                        writer = { delay(10L); memory.emit(it) },
                        loader = { delay(50L) }
                    )
                }

                // Scheduling a new refresh, it will force extra refresh every second time
                val job = launch {
                    delay(10L)
                    (if (j % 2 == 1) inflow.refreshForced() else inflow.refresh()).join()
                }

                inflow.refresh().await()
                job.join()
                inflow.cache().first { it != null }

                assertTrue(inflow.refreshState().first() is Idle, "Loading is finished $i/$j")
            }
        }
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF observe single inflow from several threads THEN no deadlocks`() = runReal {
        val cacheState = AtomicInt()

        val inflow = inflow<Unit?> {
            val memory = MutableSharedFlow<Unit?>(replay = 1).apply { tryEmit(null) }
            val cache = memory
                .onStart { cacheState.getAndIncrement() }
                .onCompletion { cacheState.decrementAndGet() }
            data(cache) { delay(100L); memory.emit(Unit) }
            keepCacheSubscribedTimeout(1L)
            retryTime(Long.MAX_VALUE)
        }

        runStressTest {
            inflow.data().first { it != null }
            inflow.refreshState().first { it is Idle }
        }

        assertEquals(expected = 0, actual = cacheState.get(), "Cache job is finished")
    }

}
