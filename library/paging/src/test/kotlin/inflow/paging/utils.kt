@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.Inflow
import inflow.base.testDispatcher
import inflow.paging.internal.PagedImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope


@ExperimentalCoroutinesApi
fun TestCoroutineScope.testPagedInflow(
    block: PagedInflowConfig<Int>.() -> Unit
): Inflow<Paged<Int>> = inflowPaged {
    dispatcher(testDispatcher)
    block()
}

val pagedInitial: Paged<Int> = PagedImpl(emptyList(), true)
