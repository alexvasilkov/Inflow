package inflow.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

internal fun Job.doOnCancel(action: (CancellationException) -> Unit) =
    invokeOnCompletion { if (it is CancellationException) action(it) }
