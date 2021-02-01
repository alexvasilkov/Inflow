@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.operators

import inflow.base.AtomicInt
import inflow.base.BaseTest
import inflow.base.STRESS_TAG
import inflow.base.STRESS_TIMEOUT
import inflow.base.runReal
import inflow.base.runStressTest
import inflow.base.runTest
import inflow.internal.doWhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class DoWhileSubscribedTest : BaseTest() {

    @Test
    fun `IF collecting THEN job started and stopped`() = runTest {
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
    fun `IF collecting from several threads THEN job is started and stopped`() = runReal {
        val state = AtomicInt()
        val flow = MutableStateFlow(0)
        val tracked = flow.doWhileSubscribed {
            launch {
                state.getAndIncrement()

                suspendCancellableCoroutine {
                    it.invokeOnCancellation {
                        state.decrementAndGet()
                    }
                }
            }
        }

        runStressTest { tracked.first() }

        assertEquals(expected = 0, actual = state.get(), "Tracked job is cancelled")
    }

}
