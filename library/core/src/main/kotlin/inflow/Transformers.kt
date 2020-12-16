package inflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

fun <T, R> Inflow<T>.map(mapper: suspend (T) -> R): Inflow<R> {
    val orig = this
    return object : Inflow<R> {
        private var cache: Flow<R>? = null
        private var auto: Flow<R>? = null

        override fun data(autoRefresh: Boolean): Flow<R> {
            // Minor optimization to avoid creating new flows on every call
            return if (autoRefresh) {
                if (auto == null) auto = orig.data(autoRefresh = true).map(mapper)
                auto!!
            } else {
                if (cache == null) cache = orig.data(autoRefresh = false).map(mapper)
                cache!!
            }
        }

        override fun loading() = orig.loading()

        override fun error() = orig.error()

        override fun refresh(repeatIfRunning: Boolean): InflowDeferred<R> {
            val resultOrig = orig.refresh(repeatIfRunning)
            return object : InflowDeferred<R> {
                override suspend fun await(): R = mapper.invoke(resultOrig.await())

                override suspend fun join() = resultOrig.join()
            }
        }
    }
}
