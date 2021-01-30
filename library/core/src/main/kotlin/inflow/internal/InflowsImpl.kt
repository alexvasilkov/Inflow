package inflow.internal

import inflow.Cache
import inflow.Inflow
import inflow.Inflows
import inflow.InflowsConfig

/**
 * Simple [Inflows] implementation that creates new [Inflow]s using provided
 * [InflowsConfig.factory] and caches them using [InflowsConfig.cache].
 */
internal class InflowsImpl<P, T>(config: InflowsConfig<P, T>) : Inflows<P, T> {

    private val factory = requireNotNull(config.factory) { "Inflows factory is required" }
    private val cache = config.cache ?: Cache.build()

    override fun get(param: P): Inflow<T> = cache.get(param, factory)

    override fun clear() = cache.clear()

}
