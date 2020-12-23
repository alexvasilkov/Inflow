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

internal class AtomicBoolean {
    private val atomic = atomic(false)

    fun set(value: Boolean) {
        atomic.value = value
    }

    fun compareAndSet(expect: Boolean, update: Boolean): Boolean =
        atomic.compareAndSet(expect, update)
}
