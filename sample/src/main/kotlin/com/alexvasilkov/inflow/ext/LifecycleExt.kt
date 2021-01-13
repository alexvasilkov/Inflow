package com.alexvasilkov.inflow.ext

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Runs the [action] if this [LifecycleOwner] is in started state. The action will be cancelled when
 * the owner is stopped and started again when the owner is started.
 */
fun LifecycleOwner.whileStarted(action: suspend () -> Unit) =
    lifecycle.addObserver(WhileStartedObserver(action))

/**
 * Collects this flow while [owner] is in started state. The flow will be unsubscribed when the
 * owner is stopped and re-subscribed when it is started again.
 */
inline fun <T> Flow<T>.whileStarted(
    owner: LifecycleOwner,
    crossinline collector: suspend (T) -> Unit
) = owner.whileStarted { collect(collector) }


private class WhileStartedObserver(
    private val action: suspend () -> Unit
) : DefaultLifecycleObserver {
    private var job: Job? = null

    override fun onStart(owner: LifecycleOwner) {
        // Global scope is fine as we'll control the lifespan of the job manually.
        // If the action crashes then crashing global scope is also fine.
        job = GlobalScope.launch(Dispatchers.Main.immediate) { action() }
    }

    override fun onStop(owner: LifecycleOwner) {
        job?.cancel()
        job = null
    }

    override fun onDestroy(owner: LifecycleOwner) {
        owner.lifecycle.removeObserver(this)
    }
}
