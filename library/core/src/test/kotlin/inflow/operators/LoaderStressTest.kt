package inflow.operators

import inflow.BaseTest
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.internal.Loader
import inflow.utils.AtomicInt
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoaderStressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Only one action can run at a time + join`() = runBlocking(Dispatchers.IO) {
        val loads = AtomicInt()
        val loader = Loader(logId, this) {
            delay(100L)
            loads.getAndIncrement()
        }

        runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = false).join() }

        println("Loads: ${loads.get()}")
        val expected = STRESS_RUNS / 4 /* per ms */ / 100 /* ms */
        val expectedRange = (expected - 3)..(expected + 1)
        assertTrue(loads.get() in expectedRange, "One action can run at a time")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Repeat-if-running results in a single item + await`() = runBlocking(Dispatchers.IO) {
        val loads = AtomicInt()
        val loader = Loader(logId, this) {
            delay(100L)
            loads.getAndIncrement()
        }

        val expected = STRESS_RUNS / 4 /* per ms */ / 100 /* ms */ + 1
        val expectedRange = (expected - 3)..(expected + 1)

        runStressTest(logId, STRESS_RUNS) {
            val result = loader.load(repeatIfRunning = true).await()
            // All waiters should receive the latest loaded item
            assertTrue(result in expectedRange, "All waiters get same results")
        }

        println("Loads: ${loads.get()}")
        assertTrue(loads.get() in expectedRange, "One action can run at a time")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Repeat-if-running results in a single item + await2`() = runBlocking(Dispatchers.IO) {
        val loader = Loader(logId, this) {}
        runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = true).await() }
        assertFalse(loader.loading.value, "Finished in the end")
    }

}
