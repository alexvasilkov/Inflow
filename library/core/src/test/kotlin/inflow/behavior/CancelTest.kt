@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.behavior

import inflow.Inflow
import inflow.LoadParam
import inflow.base.BaseTest
import inflow.base.runTest
import inflow.base.testInflow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class CancelTest : BaseTest() {

    @Test
    fun `IF scope is cancelled before creation THEN cache throws`() = runTest {
        val scope = CoroutineScope(EmptyCoroutineContext)
        scope.cancel()
        val inflow = testInflow {
            data(flowOf(0)) {}
            scope(scope)
        }
        checkCacheHasException(inflow)
    }

    @Test
    fun `IF scope is cancelled before cache subscription THEN cache throws`() = runTest {
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = testInflow {
            data(flowOf(0)) {}
            scope(scope)
        }
        scope.cancel()
        checkCacheHasException(inflow)
    }

    @Test
    fun `IF scope is cancelled during cache subscription THEN cache throws`() = runTest {
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = testInflow {
            data(flow { emit(0); delay(100L); emit(1) }) {}
            scope(scope)
        }
        val item = checkCacheHasException(inflow, onSubscribe = { scope.cancel() })
        assertEquals(expected = 0, item, "First item was collected")
    }

    private fun CoroutineScope.checkCacheHasException(
        inflow: Inflow<Int?>,
        onSubscribe: () -> Unit = {}
    ): Int? {
        var item: Int? = null
        var exception: CancellationException? = null
        launch {
            try {
                inflow.cache().collect { item = it }
            } catch (ex: CancellationException) {
                exception = ex
            }
        }

        onSubscribe()

        assertNotNull(exception, "Cancellation exception")
        return item
    }


    @Test
    fun `IF scope is cancelled THEN automatic refresh is cancelled`() = runTest { job ->
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = testInflow {
            var count = 0
            data(initial = null) { count++ }
            // null at 0, 0 at 100, 1 at 200, etc:
            expiration { data -> (data ?: -1) * 100L + 100L - currentTime }
            scope(scope)
        }

        var item: Int? = null
        launch(job) { inflow.data().collect { item = it } } // Auto refresh started
        assertEquals(expected = 0, actual = item, "First value loaded")

        delay(100L)
        assertEquals(expected = 1, actual = item, "Second value loaded")

        scope.cancel()
        delay(1000L)
        assertEquals(expected = 1, actual = item, "No third value ever loaded")
    }

    @Test
    fun `IF scope is cancelled THEN refresh does not work`() = runTest {
        val scope = CoroutineScope(EmptyCoroutineContext)
        var loaderCalled = false
        val inflow = testInflow {
            data(initial = 0) { loaderCalled = true; 1 }
            scope(scope)
        }
        scope.cancel()

        inflow.refresh()
        assertFalse(loaderCalled, "Loader is not called after refresh")

        inflow.refresh(force = true)
        assertFalse(loaderCalled, "Loader is not called after refreshForced")
    }

    @Test
    fun `IF scope is cancelled THEN refresh join() is immediate`() = runTest { job ->
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
            scope(scope)
        }
        scope.cancel()

        var joined = false
        launch(job) {
            inflow.refresh().join()
            joined = true
        }
        assertTrue(joined, "join() returns immediately")
    }

    @Test
    fun `IF scope is cancelled THEN refresh await() is cancelled`() {
        testAwaitIsCancelled(LoadParam.Refresh)
        testAwaitIsCancelled(LoadParam.RefreshIfExpired(0L))
    }

    private fun testAwaitIsCancelled(param: LoadParam) = runTest { job ->
        val scope = CoroutineScope(EmptyCoroutineContext)
        val inflow = testInflow {
            data(initial = null) { delay(100L); 0 }
            scope(scope)
        }
        scope.cancel()

        var awaitCancelled = false
        launch(job) {
            try {
                inflow.loadInternal(param).await()
            } catch (ce: CancellationException) {
                awaitCancelled = true
            }
        }
        assertTrue(awaitCancelled, "await() is immediately cancelled")
    }


    @Test
    fun `IF scope is cancelled during refresh THEN loader job is cancelled`() = runTest {
        val scope = CoroutineScope(EmptyCoroutineContext)
        var loaderFinished = false
        val inflow = testInflow {
            data(initial = 0) { delay(100L); loaderFinished = true; 1 }
            scope(scope)
        }

        inflow.refresh()

        delay(50L)
        scope.cancel()
        delay(5000L)
        assertFalse(loaderFinished, "Loader is cancelled in the middle")
    }

}
