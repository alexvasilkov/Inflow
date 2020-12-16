package inflow.operators

import inflow.BaseTest
import inflow.internal.Loader
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals

class LoaderStressTest : BaseTest() {

    @Test(timeout = 10_000L)
    fun `Only one action can run at a time + join()`() = runBlocking(Dispatchers.IO) {
        val runs = 10_000
        val loads = AtomicInteger(0)
        val loader = Loader(logId, this) {
            delay(100L)
            loads.incrementAndGet()
        }

        runStressTest(logId, runs) { loader.load(repeatIfRunning = false).join() }

        // There should be 25 actual loads: 10_000 / 4 (per millisecond) / 100
        println("Loads: ${loads.get()}")
        assertEquals(expected = 25, actual = loads.get(), "One action can run at a time")
    }

    @Test(timeout = 10_000L)
    fun `Repeat-if-running results in a single item + await()`() = runBlocking(Dispatchers.IO) {
        val runs = 10_000
        val loads = AtomicInteger(0)
        val loader = Loader(logId, this) {
            delay(100L)
            loads.incrementAndGet()
        }

        runStressTest(logId, runs) {
            val result = loader.load(repeatIfRunning = true).await()
            // All waiters should receive the latest loaded item
            assertEquals(expected = 26, actual = result, "All waiters get same results")
        }

        // There should be 26 actual loads: (10_000 / 4 (per millisecond) / 100) + 1
        println("Loads: ${loads.get()}")
        assertEquals(expected = 26, actual = loads.get(), "One action can run at a time")
    }

}
