package inflow.internal

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.reentrantLock
import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch


/**
 * Shares a flow among several subscribers. The flow is only subscribed if there is at least one
 * downstream subscriber and unsubscribed after `keepSubscribedTimeout` since last subscriber is
 * gone.
 *
 * We could use Kotlin's `.shareIn()` operator but it spins a never ending collecting job
 * while we want to have no hanging jobs once there are no cache subscribers left.
 *
 * Implemented as a class mainly because AtomicFU only allows atomics as final class members.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class SharedFlowProvider<T>(
    flow: Flow<T>,
    scope: CoroutineScope,
    keepSubscribedTimeout: Long
) {

    val shared: Flow<T>

    private val lock = reentrantLock()
    private val subscriptionsCount = atomic(0)
    private val collectJobRef = atomic<Job?>(null)
    private val completeJobRef = atomic<Job?>(null)

    init {
        val internalSharedFlow = MutableSharedFlow<T>(replay = 1)
        shared = internalSharedFlow
            .onSubscription {
                val completeJob = lock.withLock {
                    // We are only interested in the first subscription
                    if (subscriptionsCount.getAndIncrement() != 0) return@onSubscription
                    // Getting completion job under lock
                    completeJobRef.getAndSet(null)
                }

                // Cancelling completion job, if running
                completeJob?.cancelAndJoin()

                // Collector job may still be active if it is not unsubscribed by timeout yet.
                // We'll only start new collect job if there is no other active job.
                val collectJob = Job()
                if (collectJobRef.compareAndSet(expect = null, update = collectJob)) {
                    scope.launch(collectJob) {
                        flow.collect(internalSharedFlow::emit)
                        awaitCancellation() // Waiting forever in case if flow is finite
                    }
                }
            }
            .onCompletion {
                val completeJob = lock.withLock {
                    // We are only interested in the last un-subscription
                    if (subscriptionsCount.decrementAndGet() != 0) return@onCompletion
                    // Creating completion job under lock
                    Job().apply { completeJobRef.value = this }
                }

                // Scheduling collector job cancellation
                scope.launch(completeJob) {
                    delay(keepSubscribedTimeout)

                    // Cancelling latest collector job
                    val collectJob = collectJobRef.getAndSet(null)
                    if (collectJob != null) {
                        collectJob.cancel()
                        internalSharedFlow.resetReplayCache() // We cannot keep reply cache anymore
                    }
                }
            }
    }

}
