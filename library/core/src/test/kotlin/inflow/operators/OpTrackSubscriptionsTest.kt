package inflow.operators

import inflow.BaseTest
import inflow.utils.doWhileSubscribed
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class OpTrackSubscriptionsTest : BaseTest() {

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

}
