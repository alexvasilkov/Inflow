package inflow.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

internal fun CoroutineScope.doOnCancel(
    dispatcher: CoroutineDispatcher,
    action: (CancellationException) -> Unit
) {
    // Launching a never-ending coroutine that will wait for the scope cancellation
    val awaitJob = launch(dispatcher) { awaitCancellation() }
    awaitJob.invokeOnCompletion { action(it as CancellationException) } // It can only complete if cancelled
}

internal fun Job.doOnCancel(action: (CancellationException) -> Unit) {
    invokeOnCompletion {
        if (it is CancellationException) action(it)
    }
}
