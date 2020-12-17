package inflow.operators

import inflow.BaseTest
import inflow.STRESS_RUNS
import inflow.STRESS_TAG
import inflow.STRESS_TIMEOUT
import inflow.internal.doWhileSubscribed
import inflow.utils.runStressTest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DoWhileSubscribedTest : BaseTest() {

    @Test
    fun `Can track flow subscribers`() = runBlockingTest {
        val flow = MutableStateFlow(0)

        var subscribed = false
        val tracked = flow.doWhileSubscribed {
            launch {
                subscribed = true

                suspendCancellableCoroutine {
                    it.invokeOnCancellation {
                        subscribed = false
                    }
                }
            }
        }

        delay(100L)

        assertFalse(subscribed, "Not subscribed in the beginning")

        launch {
            // Subscribing and waiting a bit to allow value observation
            tracked.take(1).collect { delay(100L) }
        }

        assertTrue(subscribed, "Subscribed")

        delay(100L)

        assertFalse(subscribed, "Not subscribed in the end")
    }

    @Test
    @Tag(STRESS_TAG)
    @Timeout(STRESS_TIMEOUT)
    fun `Can track flow subscribers -- real threading`(): Unit = runBlocking(Dispatchers.IO) {
        val state = AtomicInteger(0)
        val flow = MutableStateFlow(0)
        val tracked = flow.doWhileSubscribed {
            launch {
                state.incrementAndGet()

                suspendCancellableCoroutine {
                    it.invokeOnCancellation {
                        state.decrementAndGet()
                    }
                }
            }
        }

        runStressTest(logId, STRESS_RUNS) { tracked.first() }

        assertEquals(expected = 0, actual = state.get(), "Tracked job is cancelled")
    }

}
