@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.base.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class PagerByPageTest : BaseTest() {

    @Test
    fun `By page, no identity`() {
        runPagerTests(TestSuite(hasId = false), PagerByPageTest::RemoteByPage)
    }

    @Test
    fun `By page, identify by field`() {
        runPagerTests(TestSuite(hasId = true), PagerByPageTest::RemoteByPage) {
            identifyBy { it }
        }
    }

    @Test
    fun `By page, identify by comparator`() {
        runPagerTests(TestSuite(hasId = true), PagerByPageTest::RemoteByPage) {
            identifyWith { n1, n2 -> n1 == n2 }
        }
    }


    class TestSuite(hasId: Boolean) : PagerTestSuite() {
        override val `!1 next(1) - next()` = Expect(1)
        override val `!1 next(1) - next(0)` = Expect(1, 0, more)
        override val `!1 next(1) - next(1)` = if (hasId) Expect(1, more) else Expect(1, 1, more)
        override val `!1 next(1) - next(2)` = Expect(1, 2, more)
        override val `!1 next(1) - next(2, 3)` = Expect(1, 2, 3, more)

        override val `next(1, 2, 3) - next()` = Expect(1, 2, 3)
        override val `next(1, 2, 3) - next(3)` = if (!hasId) Expect(1, 2, 3, 3) else Expect(1, 2, 3)
        override val `next(1, 2, 3) - next(4)` = Expect(1, 2, 3, 4)
        override val `next(1, 2, 3) - next(1)` = if (!hasId) Expect(1, 2, 3, 1) else Expect(2, 3, 1)
        override val `next(1, 2, 3) - next(2, 1)` =
            if (!hasId) Expect(1, 2, 3, 2, 1) else Expect(3, 2, 1)

        override val `!2 next(1, 2) - next(0, 1) - next(2)` =
            if (!hasId) Expect(1, 2, 0, 1, 2) else Expect(0, 1, 2)

        override val `next(1) - refresh()` = Expect()
        override val `next(1) - refresh(1)` = Expect(1)
        override val `next(1) - refresh(2)` = Expect(2)
        override val `next(1) - refresh(-2, -1, 0)` = Expect(-2, -1, 0, more)

        override val `next(1, 2) - refresh()` = Expect()
        override val `next(1, 2) - refresh(1)` = Expect(1)
        override val `next(1, 2) - refresh(2)` = Expect(2)
        override val `next(1, 2) - refresh(1, 2)` = Expect(1, 2)
        override val `next(1, 2, 3) - refresh(1, 2, 3)` = Expect(1, 2, 3, more)
        override val `next(1, 2, 3) - refresh()` = Expect()
        override val `next(1, 2, 3) - refresh(0)` = Expect(0)
        override val `next(1, 2, 3) - refresh(1)` = Expect(1)
        override val `next(1, 2, 3) - refresh(2)` = Expect(2)
        override val `next(1, 2, 3) - refresh(3)` = Expect(3)
        override val `next(1, 2, 3) - refresh(4)` = Expect(4)
        override val `next(1, 2, 3) - refresh(0, 1, 2)` = Expect(0, 1, 2, more)
    }


    /** Remote source with simple page number & page size pagination. */
    private class RemoteByPage : PagerTestRemote() {
        override fun load(params: PageParams<Int>): PageResult<Item, Int> {
            val page = params.key ?: 1
            val pageSize = params.count

            val items = load(page, pageSize)
            val nextKey = if (items.size >= pageSize) page + 1 else null

            return PageResult(items, nextKey)
        }

        /** Simulating remote API. Loading by page number & page size. */
        @Suppress("UNUSED_PARAMETER")
        private fun load(page: Int, pageSize: Int): List<Item> {
            return result
        }
    }

}
