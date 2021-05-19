package inflow.internal

import inflow.DataLoader
import inflow.DataProvider
import inflow.Inflow
import inflow.InflowCombined
import inflow.InflowConfig
import inflow.InflowDeferred
import inflow.LoadParam
import inflow.State
import inflow.StateParam
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow

/** Internal APIs that have to be publicly accessed from other modules. */
@InternalInflowApi
public object InternalAccess {

    public fun <T> setData(
        target: InflowConfig<T>,
        cache: Flow<T>,
        refresh: DataLoader<Unit>,
        loadNext: DataLoader<Unit>?
    ) {
        target.data = DataProvider(cache, refresh, loadNext)
    }

    @ExperimentalCoroutinesApi
    public fun <T> createInflow(config: InflowConfig<T>): Inflow<T> = InflowImpl(config)

    public fun <T> loadNext(inflow: Inflow<T>): InflowDeferred<T> =
        inflow.loadInternal(LoadParam.LoadNext)

    public fun <T> loadNextState(inflow: Inflow<T>): Flow<State> =
        inflow.stateInternal(StateParam.LoadNextState)

    public fun <T> loadNextState(combined: InflowCombined<T>): State? = combined.loadNext

}
