@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.base.BaseTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.jupiter.api.Test

@ExperimentalCoroutinesApi
class PagerByValueTest : BaseTest() {

    /* ------------------------------------------------------------------------------------------ */
    /* Tests                                                                                      */
    /* ------------------------------------------------------------------------------------------ */

    @Test
    fun `By value paging`() {
        // We need to test several configurations combinations:
        // - no identity / id by field / id by comparator
        // - no merger / merge by field / merge by comparator
        // - replace on refresh / prepend on refresh
        // - unique / non-unique items order

        // It's easier to test all at once than creating (and naming) all 28 different combinations

        var total = 0
        var errors = 0

        for (flags in 0..TestState.max) {
            val state = TestState(flags)
            if (!state.isValid) continue

            // Building remote source. If items sorting order is not unique (different items can
            // have same order) then the source is assumed to include items from pages edges.
            fun remote() = when {
                state.prepend -> RemoteByValueWithPrepend()
                else -> RemoteByValue()
            }

            val suite = TestSuite(state)

            try {
                total++
                println("-------------------------------------------------------------------------")
                println("Test #$total: $state")
                runPagerTests(suite, ::remote) {
                    if (state.idByField) identifyBy(Item::id)
                    if (state.idByComparator) identifyWith(Item::equals)
                    if (state.mergeByKey)
                        mergeBy(Item::order, { o1, o2 -> o1.compareTo(o2) }, unique = state.unique)
                    if (state.mergeByComparator)
                        mergeWith({ o1, o2 -> o1.compareTo(o2) }, unique = state.unique)
                }
            } catch (err: AssertionError) {
                errors++
            }
        }

        if (errors > 0) throw AssertionError("$errors of $total tests failed, see console output")
    }

    private class TestState(flags: Int) {
        val idByField = flags.has(idByFieldFlag)
        val idByComparator = flags.has(idByComparatorFlag)
        val mergeByKey = flags.has(mergeByKeyFlag)
        val mergeByComparator = flags.has(mergeByComparatorFlag)
        val unique = flags.has(uniqueFlag)
        val prepend = flags.has(prependFlag)

        val hasId = idByField || idByComparator
        val hasMerger = mergeByKey || mergeByComparator

        val isValid = when {
            idByField && idByComparator -> false // Can't have two types of identity at once
            mergeByKey && mergeByComparator -> false // Can't have two types of merger at once
            hasMerger && !hasId -> false // Must have id if merge is set
            else -> true
        }

        @ExperimentalStdlibApi
        override fun toString() = buildList {
            if (idByField) add("idByField")
            if (idByComparator) add("idByComparator")
            if (mergeByKey) add("mergeByKey")
            if (mergeByComparator) add("mergeByComparator")
            if (unique) add("unique")
            if (prepend) add("prepend")
        }.joinToString(prefix = "[", postfix = "]")

        companion object {
            private const val idByFieldFlag = 1.shl(0)
            private const val idByComparatorFlag = 1.shl(1)
            private const val mergeByKeyFlag = 1.shl(2)
            private const val mergeByComparatorFlag = 1.shl(3)
            private const val uniqueFlag = 1.shl(4)
            private const val prependFlag = 1.shl(5)
            const val max = 1.shl(6) - 1

            private fun Int.has(flag: Int) = and(flag) == flag
        }
    }


    /* ------------------------------------------------------------------------------------------ */
    /* Test cases                                                                                 */
    /* ------------------------------------------------------------------------------------------ */

    @Suppress("unused", "PropertyName")
    private class TestSuite(state: TestState) : PagerTestSuite() {
        private val mergeByKeyInclusive = state.mergeByKey && !state.unique
        // TODO: case for "mergeByKey && unique" vs "hasId"
        // TODO: case for "mergeByComparator" vs "hasId"

        override val `!1 next(1) - next()` = when {
            mergeByKeyInclusive -> Expect() // Repeat of 1 was expected
            else -> Expect(1)
        }

        override val `!1 next(1) - next(0)` = when {
            // Note: returning 0 after 1 is considered invalid if we expect items to be ordered.
            state.hasMerger -> Expect(ignore = true)
            else -> Expect(1, 0, more)
        }

        override val `!1 next(1) - next(1)` = when {
            state.hasId -> Expect(1, more) // First 1 is removed as duplicate
            else -> Expect(1, 1, more)
        }

        override val `!1 next(1) - next(2)` = when {
            mergeByKeyInclusive -> Expect(2, more) // Repeat of 1 was expected
            else -> Expect(1, 2, more)
        }

        override val `!1 next(1) - next(2, 3)` = when {
            mergeByKeyInclusive -> Expect(2, 3, more) // Repeat of 1 was expected
            else -> Expect(1, 2, 3, more)
        }

        override val `next(1, 2, 3) - next()` = when {
            mergeByKeyInclusive -> Expect(1, 2) // Repeat of 3 was expected
            else -> Expect(1, 2, 3)
        }

        override val `next(1, 2, 3) - next(3)` = when {
            state.hasId -> Expect(1, 2, 3) // First 3 is removed as duplicate
            else -> Expect(1, 2, 3, 3)
        }

        override val `next(1, 2, 3) - next(4)` = when {
            mergeByKeyInclusive -> Expect(1, 2, 4) // Repeat of 3 was expected
            else -> Expect(1, 2, 3, 4)
        }

        override val `next(1, 2, 3) - next(1)` = when {
            // Note: returning 1 after 3 is considered invalid if we expect items to be ordered.
            state.hasMerger -> Expect(ignore = true)
            state.hasId -> Expect(2, 3, 1)
            else -> Expect(1, 2, 3, 1)
        }

        override val `next(1, 2, 3) - next(2, 1)` = when {
            // Note: returning [2,1] after 3 is considered invalid if we expect items to be ordered.
            state.hasMerger -> Expect(ignore = true)
            state.hasId -> Expect(3, 2, 1)
            else -> Expect(1, 2, 3, 2, 1)
        }

        override val `!2 next(1, 2) - next(0, 1) - next(2)` = when {
            // Note: returning [0,1] after 2 is considered invalid if we expect items to be ordered.
            state.hasMerger -> Expect(ignore = true)
            state.hasId -> Expect(0, 1, 2)
            else -> Expect(1, 2, 0, 1, 2)
        }


        override val `next(1) - refresh()` = when {
            state.prepend && mergeByKeyInclusive -> Expect() // Repeat of 1 was expected
            state.prepend -> Expect(1)
            else -> Expect()
        }

        override val `next(1) - refresh(1)` = when {
            state.prepend && !state.hasId -> Expect(1, 1)
            else -> Expect(1)
        }

        override val `next(1) - refresh(2)` = when {
            // Note: returning 2 before 1 is considered invalid if we expect items to be ordered.
            state.hasMerger -> Expect(ignore = true)
            state.prepend -> Expect(2, 1)
            else -> Expect(2)
        }

        // TODO
        override val `next(1) - refresh(-2, -1, 0)` = Expect(ignore = true)

        override val `next(1, 2) - refresh()` = Expect(ignore = true)

        override val `next(1, 2) - refresh(1)` = Expect(ignore = true)

        override val `next(1, 2) - refresh(2)` = Expect(ignore = true)

        override val `next(1, 2) - refresh(1, 2)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(1, 2, 3)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh()` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(0)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(1)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(2)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(3)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(4)` = Expect(ignore = true)

        override val `next(1, 2, 3) - refresh(0, 1, 2)` = Expect(ignore = true)


        val `next(1^0, 2^1, 3^1) - next(4^1)` = when {
            state.mergeByKey && !state.unique -> Expect(1, 4)
            state.mergeByComparator && state.unique -> Expect(1, 4)
            else -> Expect(1, 2, 3, 4)
        }

//        val `(1^0, 2^1, 3^2, 4^2, 5^3) - next - next` = when {
//            // "4" (order=2) item is missing because next page is non-inclusive (order > 2)
//            state.unique -> Expect(1, 2, 3, 5)
//            state.hasId -> Expect(1, 2, 3, 4, 5, more)
//            else -> Expect(1, 2, 3, 3, 4, 5, more)
//        }
//
//        val `(1^0, 2^1, 3^2) - next - (1^0, 2^1, 4^2) - next` = when {
//            state.unique -> Expect(1, 2, 3)
//            // "3" (order=2) items is removed because merger by key expected all items with order=2
//            state.mergeByKey -> Expect(1, 2, 4)
//            else -> Expect(1, 2, 3, 4)
//        }
//
//        // TODO...
//        val `(1^0, 2^1, 3^1) - next - (1^0, 2^1, 4^1) - next` = when {
//            state.unique -> Expect(1, 2, 3)
//            // "3" (order=1) items is removed because merger by key expected all items with order=1
//            state.mergeByKey -> Expect(1, 2, 4)
//            state.hasId -> Expect(1, 3, 2, 4)
//            else -> Expect(1, 2, 3, 2, 4)
//        }
//
//        val `(1^0, 2^1, 3^2) - next - (1^0, 2^1, 4^2, 3^2) - next` = when {
//            state.unique -> Expect(1, 2, 3)
//            state.hasId -> Expect(1, 2, 4, 3)
//            else -> Expect(1, 2, 3, 4, 3)
//        }

        // TODO: Test merger with prepends
    }


    /* ------------------------------------------------------------------------------------------ */
    /* Remote sources                                                                             */
    /* ------------------------------------------------------------------------------------------ */

    /** Remote source that uses "replace" strategy for refresh. */
    private class RemoteByValue : BaseRemoteByValue() {

        override fun load(params: PageParams<Int>): PageResult<Item, Int> {
            val items = loadFromKey(params.key, params.count)
            val nextKey = if (items.size >= params.count) items.last().order else null
            return PageResult(items, nextKey)
        }

    }

    /** Remote source that uses "prepend" strategy for refresh. */
    private class RemoteByValueWithPrepend : BaseRemoteByValue() {
        override fun load(params: PageParams<Int>): PageResult<Item, Int> {
            return when {
                // Load first page
                params.key == null -> loadNext(null, params.count)
                // Load further pages
                params is PageParams.Next -> loadNext(params.key, params.count)
                // Load and prepend newer items
                params is PageParams.Refresh -> loadNewer(params.key!!, params.count)
                // Should never be the case
                else -> throw IllegalArgumentException()
            }
        }

        private fun loadNext(from: Int?, count: Int): PageResult<Item, Int> {
            val items = loadFromKey(from, count)
            return PageResult(
                items = items,
                nextKey = if (items.size >= count) items.last().order else null,
                refreshKey = items.firstOrNull()?.order,
                forceClearCacheOnRefresh = from == null // Clear cache when loading first page
            )
        }

        private fun loadNewer(to: Int, count: Int): PageResult<Item, Int> {
            val new = loadToKey(to, count).asReversed()
            return when {
                // If no newer items then refresh key should stay unchanged
                new.isEmpty() -> PageResult(items = emptyList(), nextKey = null, refreshKey = to)
                // If we sure that the newer items list is exhaustive then we can prepend it as-is
                new.size < count -> PageResult(new, nextKey = null, refreshKey = new.first().order)
                // Otherwise we have to reload first page and force replace the local cache
                else -> loadNext(null, count)
            }
        }
    }


    @Suppress("UNUSED_PARAMETER")
    private abstract class BaseRemoteByValue : PagerTestRemote() {
        /** Simulating remote API. Loading next page starting from [key]. */
        protected fun loadFromKey(key: Int?, count: Int): List<Item> = result

        /** Simulating remote API. Loading items newer than [key], upward. */
        protected fun loadToKey(key: Int, count: Int): List<Item> = result.asReversed()
    }

}
