package inflow

import kotlinx.coroutines.flow.StateFlow

public interface InflowConnectivity {

    /**
     * A flow which should emit `true` each time when connectivity becomes available
     * and `false` when connectivity is lost.
     */
    public val connected: StateFlow<Boolean>

    public companion object {
        /**
         * Default connectivity to be used by all newly created [Inflow].
         *
         * This can be set globally to avoid passing [InflowConnectivity] on every [Inflow]
         * creation.
         *
         * Default value is `null`, unless it's set by `inflow-android` module.
         */
        public var Default: InflowConnectivity? = null
    }

}
