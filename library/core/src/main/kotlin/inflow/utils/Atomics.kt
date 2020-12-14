package inflow.utils

import kotlinx.atomicfu.atomic

/*
 * AtomicFU only allows creation of atomics as class' private read-only fields.
 */

internal class AtomicInt {
    private val atomic = atomic(0)

    fun get(): Int = atomic.value

    fun getAndIncrement(): Int = atomic.getAndIncrement()

    fun decrementAndGet(): Int = atomic.decrementAndGet()
}

internal class AtomicRef<T> {
    private val atomic = atomic<T?>(null)

    fun getAndSet(value: T?): T? = atomic.getAndSet(value)

    fun compareAndSet(expect: T?, update: T?): Boolean = atomic.compareAndSet(expect, update)
}
