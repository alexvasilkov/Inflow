package inflow.operators

import inflow.BaseTest
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.internal.Loader
import inflow.utils.AtomicInt
import inflow.utils.log
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoaderStressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=false and join() THEN only one action runs at a time`() =
        runBlocking(Dispatchers.IO) {
            val loads = AtomicInt()
            val loader = Loader(logId, this) {
                delay(100L)
                loads.getAndIncrement()
            }

            runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = false).join() }

            log(logId) { "Loads: ${loads.get()}" }
            val expected = STRESS_RUNS / 4 /* per ms */ / 100 /* ms */
            val expectedRange = (expected - 3)..(expected + 1)
            assertTrue(loads.get() in expectedRange, "One action can run at a time")
        }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=true and await() THEN all waiters get same result`() =
        runBlocking(Dispatchers.IO) {
            val loads = AtomicInt()
            val loader = Loader(logId, this) {
                delay(100L)
                loads.getAndIncrement()
            }

            var commonResult: Int? = null
            runStressTest(logId, STRESS_RUNS) {
                val result = loader.load(repeatIfRunning = true).await()
                synchronized(loader) {
                    if (commonResult == null) commonResult = result
                    // All waiters should receive the latest loaded item
                    assertEquals(commonResult, result, "All waiters get same result")
                }
            }

            log(logId) { "Loads: ${loads.get()}" }
            assertEquals(loads.get() - 1, commonResult, "All waiters get latest result")
        }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=true and await() THEN finishes with no deadlocks`() =
        runBlocking(Dispatchers.IO) {
            val loader = Loader(logId, this) {}
            runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = true).await() }
            assertFalse(loader.loading.value, "Finished in the end")
        }

}
