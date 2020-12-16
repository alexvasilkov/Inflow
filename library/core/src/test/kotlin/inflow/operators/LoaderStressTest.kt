package inflow.operators

import inflow.BaseTest
import inflow.internal.Loader
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue

class LoaderStressTest : BaseTest() {

    @Test(timeout = 15_000L)
    fun `Only one action can run at a time + join()`() = runBlocking(Dispatchers.IO) {
        val runs = 5_000
        val loads = AtomicInteger(0)
        val loader = Loader(logId, this) {
            delay(100L)
            loads.incrementAndGet()
        }

        runStressTest(logId, runs) { loader.load(repeatIfRunning = false).join() }

        // There should be around 13 actual loads: 5_000 / 4 (per millisecond) / 100
        println("Loads: ${loads.get()}")
        assertTrue(loads.get() in 10..16, "One action can run at a time")
    }

    @Test(timeout = 15_000L)
    fun `Repeat-if-running results in a single item + await()`() = runBlocking(Dispatchers.IO) {
        val runs = 5_000
        val loads = AtomicInteger(0)
        val loader = Loader(logId, this) {
            delay(100L)
            loads.incrementAndGet()
        }

        runStressTest(logId, runs) {
            val result = loader.load(repeatIfRunning = true).await()
            // All waiters should receive the latest loaded item
            assertTrue(result in 11..17, "All waiters get same results")
        }

        // There should be around 14 actual loads: (5_000 / 4 (per millisecond) / 100) + 1
        println("Loads: ${loads.get()}")
        assertTrue(loads.get() in 11..17, "One action can run at a time")
    }

}
