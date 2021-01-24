package inflow.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged

internal fun Job.doOnCancel(action: (CancellationException) -> Unit) =
    invokeOnCompletion { if (it is CancellationException) action(it) }

internal fun <T> Flow<T>.noConsequentNulls() =
    distinctUntilChanged { old, new -> old == null && new == null }
