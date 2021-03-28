package inflow

/**
 * Collection of on-demand [Inflow]s where each Inflow is created for a specific parameter.
 *
 * Regular [Inflow] is not parametrized, it is meant to only handle single predefined use-case
 * (for example load a list of items, or load an item for a specific ID). `Inflows` is designed
 * to support dynamic parametrization by creating a new [Inflow] for each parameter.
 *
 * **Usage**
 *
 * An `Inflows` instance is created using [inflows] method.
 *
 * A simple usage can look like this:
 *
 * ```
 * val companies = inflows(factory: { id: String ->
 *     inflow<Company?> {
 *         // Loading a company by ID and caching it in memory
 *         data(initial = null) { api.loadCompany(id) }
 *     }
 * })
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
 * Simple [Inflows] implementation that creates new [Inflow]s using provided [factory] and caches
 * them using the [cache].
 */
internal class InflowsImpl<P, T>(
    private val factory: (P) -> Inflow<T>,
    private val cache: InflowsCache<P, Inflow<T>>
) : Inflows<P, T> {

    override fun get(param: P): Inflow<T> = cache.get(param, factory)

    override fun clear() = cache.clear()

}
