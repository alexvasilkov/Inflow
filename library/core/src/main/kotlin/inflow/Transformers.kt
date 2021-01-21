package inflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun <T, R> Inflow<T>.map(mapper: suspend (T) -> R): Inflow<R> {
    val orig = this
    return object : Inflow<R> {
        override fun data(vararg params: DataParam): Flow<R> = orig.data(*params).map(mapper)

        override fun progress() = orig.progress()

        override fun error(vararg params: ErrorParam) = orig.error(*params)

        override fun refresh(vararg params: RefreshParam): InflowDeferred<R> {
            val resultOrig = orig.refresh(*params)
            return object : InflowDeferred<R> {
                override suspend fun await(): R = mapper.invoke(resultOrig.await())

                override suspend fun join() = resultOrig.join()
            }
        }
    }
}
