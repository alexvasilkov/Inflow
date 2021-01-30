package inflow

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public fun <T, R> Inflow<T>.map(mapper: suspend (T) -> R): Inflow<R> {
    val orig = this
    return object : Inflow<R> {
        override fun data(param: DataParam): Flow<R> = orig.data(param).map(mapper)

        override fun state(param: StateParam) = orig.state(param)

        override fun load(param: LoadParam): InflowDeferred<R> {
            val resultOrig = orig.load(param)

            return object : InflowDeferred<R> {
                override suspend fun await(): R = mapper.invoke(resultOrig.await())

                override suspend fun join() = resultOrig.join()
            }
        }
    }
}
