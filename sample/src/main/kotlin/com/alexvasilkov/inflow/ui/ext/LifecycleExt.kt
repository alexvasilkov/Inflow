package com.alexvasilkov.inflow.ui.ext

import androidx.lifecycle.Lifecycle.Event
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


// TODO: Move to android library

/**
 * Runs the [action] if this [LifecycleOwner] is in started state. The action will be cancelled when
 * the owner is stopped and started again when the owner is started.
 */
fun LifecycleOwner.whileStarted(action: suspend () -> Unit): Unit =
    lifecycle.addObserver(WhileStartedObserver(action))

/**
 * Collects this flow while [owner] is in started state. The flow will be unsubscribed when the
 * owner is stopped and re-subscribed when it is started again.
 */
inline fun <T> Flow<T>.whileStarted(
    owner: LifecycleOwner,
    crossinline collector: suspend (T) -> Unit
): Unit = owner.whileStarted { collect(collector) }


private class WhileStartedObserver(
    private val action: suspend () -> Unit
) : LifecycleEventObserver {
    private var job: Job? = null

    override fun onStateChanged(source: LifecycleOwner, event: Event) = when (event) {
        Event.ON_START -> {
            // Global scope is fine as we'll control the lifespan of the job manually.
            // If the action crashes then crashing global scope is also fine.
            job = GlobalScope.launch(Dispatchers.Main.immediate) { action() }
        }
        Event.ON_STOP -> {
            job?.cancel()
            job = null
        }
        Event.ON_DESTROY -> {
            source.lifecycle.removeObserver(this)
        }
        else -> {
            // No-op
        }
    }
}
