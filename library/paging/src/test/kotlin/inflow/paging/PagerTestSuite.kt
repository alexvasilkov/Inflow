@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

@Suppress("PropertyName", "unused")
abstract class PagerTestSuite : TestSuite() {
    val `!1 none` = Expect(more) // Always starts with hasNext = true

    // Pagination
    val `next()` = Expect()
    val `next(1)` = Expect(1)
    val `!1 next(1)` = Expect(1, more)
    val `next(1, 1)` = Expect(1, 1)
    val `next(1, 2)` = Expect(1, 2)
    val `next(1, 2, 3)` = Expect(1, 2, 3, more)
    val `next() - next(1)` = Expect(loads = 1) // No extra load if end is reached

    abstract val `!1 next(1) - next()`: Expect
    abstract val `!1 next(1) - next(0)`: Expect
    abstract val `!1 next(1) - next(1)`: Expect
    abstract val `!1 next(1) - next(2)`: Expect
    abstract val `!1 next(1) - next(2, 3)`: Expect
    abstract val `next(1, 2, 3) - next()`: Expect
    abstract val `next(1, 2, 3) - next(3)`: Expect
    abstract val `next(1, 2, 3) - next(4)`: Expect
    abstract val `next(1, 2, 3) - next(1)`: Expect
    abstract val `next(1, 2, 3) - next(2, 1)`: Expect
    abstract val `!2 next(1, 2) - next(0, 1) - next(2)`: Expect

    // Refresh
    val `refresh()` = Expect()
    val `refresh(1)` = Expect(1)
    val `refresh(1, 2)` = Expect(1, 2)
    val `next() - refresh(1, 2, 3)` = Expect(1, 2, 3, more)

    abstract val `next(1) - refresh()`: Expect
    abstract val `next(1) - refresh(1)`: Expect
    abstract val `next(1) - refresh(2)`: Expect
    abstract val `next(1) - refresh(-2, -1, 0)`: Expect
    abstract val `next(1, 2) - refresh()`: Expect
    abstract val `next(1, 2) - refresh(1)`: Expect
    abstract val `next(1, 2) - refresh(2)`: Expect
    abstract val `next(1, 2) - refresh(1, 2)`: Expect
    abstract val `next(1, 2, 3) - refresh(1, 2, 3)`: Expect
    abstract val `next(1, 2, 3) - refresh()`: Expect
    abstract val `next(1, 2, 3) - refresh(0)`: Expect
    abstract val `next(1, 2, 3) - refresh(1)`: Expect
    abstract val `next(1, 2, 3) - refresh(2)`: Expect
    abstract val `next(1, 2, 3) - refresh(3)`: Expect
    abstract val `next(1, 2, 3) - refresh(4)`: Expect
    abstract val `next(1, 2, 3) - refresh(0, 1, 2)`: Expect
}
