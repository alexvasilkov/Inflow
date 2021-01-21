@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.operators

import inflow.BaseTest
import inflow.Progress
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.internal.Loader
import inflow.utils.AtomicInt
import inflow.utils.log
import inflow.utils.runReal
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
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
    fun `IF repeatIfRunning=false and join() THEN only one action runs at a time`() = runReal {
        val loads = AtomicInt()
        val loader = Loader(logId, this, Dispatchers.Default) {
            delay(100L)
            loads.getAndIncrement()
        }

        runStressTest { loader.load(repeatIfRunning = false).join() }

        log(logId) { "Loads: ${loads.get()}" }
        // There must be much more than one loading event (around STRESS_RUNS / 4 / 100), but it is
        // impossible to predict the exact amount because of timings, so we'll just check it's > 1.
        assertTrue(loads.get() > 1, "One action should run at a time")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `IF repeatIfRunning=true and await() THEN finishes with no deadlocks`() = runReal {
        val loader = Loader(logId, this, Dispatchers.Default) {}
        runStressTest { loader.load(repeatIfRunning = true).await() }
        assertSame(Progress.Idle, loader.progress.value, "Finished")
    }

}
