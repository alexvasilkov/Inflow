package inflow.operators

import inflow.BaseTest
import inflow.utils.trackSubscriptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class OpTrackSubscriptionsTest : BaseTest() {

    @Test
    fun `Can track flow subscribers`() = runBlockingTest {
        val flow = MutableStateFlow(0)

        val scope = CoroutineScope(TestCoroutineDispatcher())

        val subscribed = MutableStateFlow(false)
        val tracked = flow.trackSubscriptions(scope, subscribed)

        delay(100L)

        assertFalse(subscribed.value, "Not subscribed in the beginning")

        launch {
            // Subscribing and waiting a bit to allow value observation
            tracked.take(1).collect { delay(100L) }
        }

        assertTrue(subscribed.value, "Subscribed")

        delay(100L)

        assertFalse(subscribed.value, "Not subscribed in the end")

        scope.cancel()
    }

}
