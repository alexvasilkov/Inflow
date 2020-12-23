package inflow.operators

import inflow.BaseTest
import inflow.Progress
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.internal.Loader
import inflow.utils.AtomicInt
import inflow.utils.log
import inflow.utils.runStressTest
import inflow.utils.runThreads
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LoaderStressTest : BaseTest() {

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=false and join() THEN only one action runs at a time`() = runThreads {
        val loads = AtomicInt()
        val loader = Loader(logId, this) { delay(100L); loads.getAndIncrement() }

        runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = false).join() }

        log(logId) { "Loads: ${loads.get()}" }
        // There must be much more than one loading event (around STRESS_RUNS / 4 / 100), but it is
        // impossible to predict the exact amount because of timings, so we'll just check it's > 1.
        assertTrue(loads.get() > 1, "One action should run at a time")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=true and await() THEN finishes with no deadlocks`() = runThreads {
        val loader = Loader(logId, this) {}
        runStressTest(logId, STRESS_RUNS) { loader.load(repeatIfRunning = true).await() }
        assertSame(Progress.Idle, loader.progress.value, "Finished")
    }

}
