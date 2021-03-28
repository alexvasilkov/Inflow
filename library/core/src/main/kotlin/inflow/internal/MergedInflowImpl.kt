package inflow.internal

import inflow.DataParam
import inflow.Inflow
import inflow.InflowDeferred
import inflow.Inflows
import inflow.LoadParam
import inflow.StateParam
import inflow.utils.doOnCancel
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * [Inflow] implementation that delegates to other Inflows created dynamically for each param from
 * params flow.
 */
@ExperimentalCoroutinesApi
internal class MergedInflowImpl<P, T>(
    inflows: Inflows<P, T>,
    params: Flow<P>,
    private val dispatcher: CoroutineDispatcher,
    private val scope: CoroutineScope
) : Inflow<T>() {

    private val shared = params
        .map { inflows[it] } // Creating a new Inflow for each parameter, or using cached one
        .distinctUntilChanged { old, new -> old === new } // Filtering duplicate Inflow instances
        .share(scope, dispatcher, 0L) // Sharing the flow to reuse params subscription

    override fun dataInternal(param: DataParam) = shared
        .flatMapLatest { it.dataInternal(param) }

    override fun stateInternal(param: StateParam) = shared
        .flatMapLatest { it.stateInternal(param) }
        .distinctUntilChanged()

    override fun loadInternal(param: LoadParam): InflowDeferred<T> {
        val deferred = DeferredDelegate<T>()
        val job = scope.launch(dispatcher) {
            deferred.delegateTo(shared.first().loadInternal(param))
        }
        // If scope is cancelled then we need to notify our deferred object
        job.doOnCancel(deferred::onCancelled)
        return deferred
    }

}

/**
 * Waits for the delegate and then delegates the actual [await] and [join] to it.
 */
private class DeferredDelegate<T> : InflowDeferred<T> {

    private val delegate = atomic<InflowDeferred<T>?>(null)
    private val cancellationException = atomic<CancellationException?>(null)
    private val notifier = Job()

    fun delegateTo(other: InflowDeferred<T>) {
        delegate.value = other
        notifier.complete()
    }

    fun onCancelled(cause: CancellationException) {
        cancellationException.value = cause
        notifier.complete()
    }

    override suspend fun await(): T {
        notifier.join()
        cancellationException.value?.let { throw it }
        return delegate.value!!.await()
    }

    override suspend fun join() {
        notifier.join()
        delegate.value?.join()
    }

}
