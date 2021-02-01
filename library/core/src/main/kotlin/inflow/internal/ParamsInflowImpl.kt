package inflow.internal

import inflow.DataParam
import inflow.Inflow
import inflow.InflowDeferred
import inflow.LoadParam
import inflow.ParamsInflowConfig
import inflow.StateParam
import inflow.inflows
import inflow.utils.doOnCancel
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
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
internal class ParamsInflowImpl<P, T>(
    params: Flow<P>,
    config: ParamsInflowConfig<P, T>
) : Inflow<T> {

    private val scope = config.scope ?: CoroutineScope(Job())
    private val dispatcher = config.dispatcher
    private val inflows = inflows<P, T> {
        config.factory?.let(::factory)
        config.cache?.let(::cache)
    }

    private val shared = params
        .map { inflows[it] } // Creating a new Inflow for each parameter or using cached one
        .distinctUntilChanged { old, new -> old === new } // Filtering duplicate Inflow instances
        .share(scope, dispatcher, 0L) // Sharing the flow to reuse params subscription

    override fun data(param: DataParam) = shared
        .flatMapLatest { it.data(param) }

    override fun state(param: StateParam) = shared
        .flatMapLatest { it.state(param) }
        .distinctUntilChanged()

    override fun load(param: LoadParam): InflowDeferred<T> {
        val deferred = DeferredDelegate<T>()
        val job = scope.launch(dispatcher) {
            deferred.delegateTo(shared.first().load(param))
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
