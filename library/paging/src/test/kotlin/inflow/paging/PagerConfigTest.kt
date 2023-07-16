@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.base.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlin.test.Test
import kotlin.test.assertFailsWith

@ExperimentalCoroutinesApi
class PagerConfigTest : BaseTest() {

    @Test
    fun `IF no page size THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflowPaged<Unit> {
                pager<Unit> {}
            }
        }
    }

    @Test
    fun `IF page size is 0 THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflowPaged<Unit> {
                pager<Unit> { pageSize(0) }
            }
        }
    }

    @Test
    fun `IF merger but no identity THEN error`() {
        assertFailsWith<IllegalArgumentException> {
            inflowPaged<Unit> {
                pager<Unit> {
                    pageSize(1)
                    mergeWith({ _, _ -> 0 }, unique = false)
                }
            }
        }
    }

}
