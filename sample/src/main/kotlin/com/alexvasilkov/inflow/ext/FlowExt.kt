package com.alexvasilkov.inflow.ext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

/**
 * Allows delegating a property to [MutableStateFlow] value, for example:
 *
 * ```
 * var searchQuery: String by stateField(searchQueryChanges)
 * ```
 */
fun <T> stateField(state: MutableStateFlow<T>) = StateFlowField(state)

class StateFlowField<T>(private val state: MutableStateFlow<T>) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state.value
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        state.value = newValue
    }
}
