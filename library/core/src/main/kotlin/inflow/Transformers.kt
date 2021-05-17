package inflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun <T, R> Inflow<T>.map(mapper: suspend (T) -> R): Inflow<R> {
    val orig = this
    return object : Inflow<R>() {
        override fun dataInternal(param: DataParam): Flow<R> = orig.dataInternal(param).map(mapper)

        override fun stateInternal(param: StateParam) = orig.stateInternal(param)

        override fun loadInternal(param: LoadParam): InflowDeferred<R> {
            val resultOrig = orig.loadInternal(param)

            return object : InflowDeferred<R> {
                override suspend fun await(): R = mapper.invoke(resultOrig.await())

                override suspend fun join() = resultOrig.join()
            }
        }

        override fun combineInternal(param: DataParam): Flow<InflowCombined<R>> =
            orig.combineInternal(param)
                .map { InflowCombined(mapper.invoke(it.data), it.refresh, it.loadNext) }
    }
}
