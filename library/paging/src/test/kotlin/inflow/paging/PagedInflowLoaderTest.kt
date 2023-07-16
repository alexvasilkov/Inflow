@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.LoadTracker
import inflow.State
import inflow.base.BaseTest
import inflow.base.maxDelay
import inflow.base.runTest
import inflow.cached
import inflow.inflow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class PagedInflowLoaderTest : BaseTest() {

    @Test
    fun `IF regular Inflow with Paged type THEN crash calling load next`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            val inflow = inflow<Paged<Int>> {
                data(initial = pagedInitial, loader = { pagedInitial }) // Data loader is required
            }
            inflow.loadNext()
        }
    }

    @Test
    fun `IF regular Inflow with Paged typew THEN crash getting load next state`() = runTest {
        assertFailsWith<UnsupportedOperationException> {
            val inflow = inflow<Paged<Int>> {
                data(initial = pagedInitial, loader = { pagedInitial }) // Data loader is required
            }
            inflow.loadNextState()
        }
    }


    @Test
    fun `IF generic pager THEN cache is observed`() = runTest {
        val inflow = testPagedInflow { pager(createPager()) }
        assertSame(expected = pagedInitial, actual = inflow.cached(), "Pager cache is observed")
    }

    @Test
    fun `IF generic pager THEN refresh is called`() = runTest {
        var called = false
        val inflow = testPagedInflow {
            pager(createPager(refresh = { called = true }))
        }
        inflow.refresh()
        assertTrue(called, "Refresh is called")
    }

    @Test
    fun `IF generic pager THEN load next is called`() = runTest {
        var called = false
        val inflow = testPagedInflow {
            pager(createPager(loadNext = { called = true }))
        }
        inflow.loadNext()
        assertTrue(called, "Load next is called")
    }

    @Test
    fun `IF generic pager THEN refresh state is observed`() = runTest { job ->
        val inflow = testPagedInflow { pager(createPager()) }

        val states = mutableListOf<State>()
        launch(job) { inflow.refreshState().collect { states += it } }

        inflow.refresh()
        maxDelay()

        val expected = listOf(State.Idle.Initial, State.Loading.Started, State.Idle.Success)
        assertEquals(expected, states, "Refresh states are observed")
    }

    @Test
    fun `IF generic pager THEN load next state is observed`() = runTest { job ->
        val inflow = testPagedInflow { pager(createPager()) }

        val states = mutableListOf<State>()
        launch(job) { inflow.loadNextState().collect { states += it } }

        inflow.loadNext()
        maxDelay()

        val expected = listOf(State.Idle.Initial, State.Loading.Started, State.Idle.Success)
        assertEquals(expected, states, "Load next states are observed")
    }


    private fun createPager(
        cache: Flow<Paged<Int>> = flowOf(pagedInitial),
        refresh: suspend (LoadTracker) -> Unit = {},
        loadNext: suspend (LoadTracker) -> Unit = {},
    ): Pager<Int> = object : Pager<Int> {
        override val display = cache
        override suspend fun refresh(tracker: LoadTracker) = refresh.invoke(tracker)
        override suspend fun loadNext(tracker: LoadTracker) = loadNext.invoke(tracker)
    }

}
