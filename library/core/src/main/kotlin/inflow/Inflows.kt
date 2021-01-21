package inflow

import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Collection of on-demand [Inflow]s where each Inflow is created for a specific parameter.
 *
 * Regular [Inflow] is not parametrized, it is meant to only handle single predefined use-case
 * (for example load a list of items, or load an item for a specific ID). `Inflows` is designed
 * to support dynamic parametrization by creating a new [Inflow] for each parameter.
 *
 * **Usage**
 *
 * An `Inflows` instance is created using [inflows] method and configured using [InflowsConfig].
 *
 * A simple usage can look like this:
 *
 * ```
 * val companies = inflows<String, Company?> {
 *     builder { id ->
 *         // Loading a company by ID and caching it in memory
 *         data(initial = null) { api.loadCompany(id) }
 *     }
 * }
 *
 * // Requesting a company with ID "42" and observing the result
 * companies["42"].data()
 *     .onEach { company -> show(company) }
 *     .launchIn(lifecycleScope)
 * ```
 *
 * **Important**: parameters of type `P` should provide a correct implementation of
 * [equals][Any.equals] and [hashCode][Any.hashCode] since they will be used as [Map] keys.
 * Primitive types and data classes are the best candidates.
 */
public interface Inflows<P, T> {
    /**
     * Returns an [Inflow] for the given [param].
     * Cached instance will be used whenever possible, otherwise a new instance will be created.
     */
    public operator fun get(param: P): Inflow<T>

    /**
     * Clears all the cached [Inflow] instances.
     */
    public fun clear()
}


/**
 * Creates a new [Inflows] instance using provided [InflowsConfig] configuration.
 */
public fun <P, T> inflows(block: InflowsConfig<P, T>.() -> Unit): Inflows<P, T> =
    InflowsImpl(InflowsConfig<P, T>().apply(block))


/**
 * Configuration params to create a new [Inflows] instance.
 *
 * It is required to provide a factory to build new [Inflow] instances either using [factory] or
 * [builder] function.
 *
 * Optional [cache] implementation can be provided to control memory usage.
 */
public class InflowsConfig<P, T> internal constructor() {

    @JvmField
    @JvmSynthetic
    internal var factory: ((P) -> Inflow<T>)? = null

    @JvmField
    @JvmSynthetic
    internal var cache: InflowsCache<P, Inflow<T>>? = null

    /**
     * A factory to build a new [Inflow] for particular parameter on demand.
     */
    public fun factory(factory: (P) -> Inflow<T>) {
        this.factory = factory
    }

    /**
     * [Inflow] builder which configures new instance for a specific parameter on demand.
     * Has similar signature to [inflow] method except that it will also accept a parameter.
     *
     * It's a syntactic sugar for [factory], the next configurations are equivalent:
     *
     * ```
     * builder { param ->
     *     ...
     * }
     * ```
     * ```
     * factory { param ->
     *     inflow {
     *         ...
     *     }
     * }
     * ```
     */
    @JvmSynthetic // Avoiding coverage report issues
    @ExperimentalCoroutinesApi
    public inline fun builder(crossinline block: InflowConfig<T>.(P) -> Unit) {
        factory { params -> inflow { block(params) } }
    }

    /**
     * Cache implementation to control how many [Inflow] instances can be stored in memory for
     * faster access.
     * Default implementation keeps up to 10 Inflow instances in memory, see [inflowsCache].
     */
    public fun cache(cache: InflowsCache<P, Inflow<T>>) {
        this.cache = cache
    }

}


/**
 * Simple [Inflows] implementation that creates new [Inflow]s using provided
 * [InflowsConfig.factory] and caches them using [InflowsConfig.cache].
 */
private class InflowsImpl<P, T>(config: InflowsConfig<P, T>) : Inflows<P, T> {

    private val factory = requireNotNull(config.factory) { "Inflows factory is required" }
    private val cache = config.cache ?: inflowsCache()

    override fun get(param: P): Inflow<T> = cache.get(param, factory)

    override fun clear() = cache.clear()

}
