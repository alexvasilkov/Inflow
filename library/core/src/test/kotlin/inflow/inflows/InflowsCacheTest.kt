@file:Suppress("NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING")

package inflow.inflows

import inflow.BaseTest
import inflow.inflowsCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class InflowsCacheTest : BaseTest() {

    @Test
    fun `IF max size is less than 1 THEN exception`() {
        assertFailsWith<IllegalArgumentException> {
            inflowsCache<Unit, Unit>(maxSize = 0)
        }
    }

    @Test
    fun `IF max size is negative THEN exception`() {
        assertFailsWith<IllegalArgumentException> {
            inflowsCache<Unit, Unit>(expireAfterAccess = -1L)
        }
    }

    @Test
    fun `IF item is cached THEN no new item is created for same key`() {
        val cache = inflowsCache<Int, Int>()

        cache.get(0) { it }

        var called = false
        cache.get(0) { called = true; it }

        assertFalse(called, "Not building the value again")
    }

    @Test
    fun `IF max size is 1 THEN old item is removed when new one is added`() {
        val cache = inflowsCache<Int, Int>(maxSize = 1)

        var removed: Int? = null
        cache.doOnRemove { removed = it }

        cache.get(0) { it }
        assertNull(removed, "First item is not removed yet")

        cache.get(1) { it }
        assertEquals(expected = 0, actual = removed, "First item is removed")
    }

    @Test
    fun `IF expireAfterAccess is used THEN expired items are removed`() {
        val cache = inflowsCache<Int, Int>(expireAfterAccess = 10L)

        var removed: Int? = null
        cache.doOnRemove { removed = it }

        cache.get(0) { it }
        assertNull(removed, "First item is not removed yet")

        Thread.sleep(6L)
        cache.get(1) { it }
        Thread.sleep(6L)

        cache.get(2) { it }
        assertEquals(expected = 0, actual = removed, "First item is removed")
    }

    @Test
    fun `IF clear is called THEN cached items are removed`() {
        val cache = inflowsCache<Int, Int>()

        cache.get(0) { it }
        cache.get(1) { it }

        val removed = mutableListOf<Int>()
        cache.doOnRemove { removed += it }

        cache.clear()

        assertEquals(expected = listOf(0, 1), actual = removed, "All items are removed")
    }

}
