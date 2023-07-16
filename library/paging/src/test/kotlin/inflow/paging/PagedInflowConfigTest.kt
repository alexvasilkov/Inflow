@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.InflowConfig
import inflow.MemoryCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class PagedInflowConfigTest {

    @Test
    fun `IF paged config THEN cannot use data(cache, loader) config`() {
        assertFailsWith<UnsupportedOperationException> {
            inflowPaged<Int> {
                val config = this as InflowConfig<Paged<Int>>
                config.data(cache = flowOf(), loader = {})
            }
        }
    }

    @Test
    fun `IF paged config THEN cannot use data(cache, writer, loader) config`() {
        assertFailsWith<UnsupportedOperationException> {
            inflowPaged<Int> {
                val config = this as InflowConfig<Paged<Int>>
                config.data(cache = flowOf(), writer = {}, loader = {})
            }
        }
    }

    @Test
    fun `IF paged config THEN cannot use data(mem cache, loader) config`() {
        assertFailsWith<UnsupportedOperationException> {
            inflowPaged<Int> {
                val config = this as InflowConfig<Paged<Int>>
                config.data(cache = MemoryCache.create(pagedInitial), loader = { pagedInitial })
            }
        }
    }

    @Test
    fun `IF paged config THEN cannot use data(initial, loader) config`() {
        assertFailsWith<UnsupportedOperationException> {
            inflowPaged<Int> {
                val config = this as InflowConfig<Paged<Int>>
                config.data(initial = pagedInitial, loader = { pagedInitial })
            }
        }
    }

}
