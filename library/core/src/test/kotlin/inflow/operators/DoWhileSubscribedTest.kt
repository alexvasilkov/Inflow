package inflow.operators

import inflow.BaseTest
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
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
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

    @Test(timeout = 10_000L)
    fun `Can track flow subscribers -- real threading`(): Unit = runBlocking(Dispatchers.IO) {
        val runs = 10_000

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

        runStressTest(logId, runs) { tracked.first() }

        assertEquals(expected = 0, actual = state.get(), "Tracked job is cancelled")
    }

}
