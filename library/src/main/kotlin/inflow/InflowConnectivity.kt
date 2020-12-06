package inflow

import android.content.Context
import inflow.impl.NetworkConnectivity
import kotlinx.coroutines.flow.Flow

interface InflowConnectivity {

    /**
     * A flow which should emit `true` each time when connectivity becomes available
     * and `false` when connectivity is lost.
     */
    val connected: Flow<Boolean>

    companion object {
        /**
         * Default connectivity to be used by all newly created [Inflow].
         *
         * This can be set globally to avoid passing [InflowConnectivity] on every [Inflow]
         * creation.
         *
         * Default value is `null` because [Network] connectivity implementation requires [Context].
         */
        var Default: InflowConnectivity? = null

        @Volatile
        private var Network: InflowConnectivity? = null

        /**
         * Connectivity provider that listens for an active network interface that can reach
         * the internet.
         */
        @Suppress("FunctionName") // Mimicking sealed class but allowing extensions
        fun Network(appContext: Context): InflowConnectivity {
            // Only creating the instance once, to not subscribe to network updates several times
            return Network ?: synchronized(this) {
                Network ?: NetworkConnectivity(appContext).also { Network = it }
            }
        }
    }

}
