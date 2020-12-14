package inflow.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import inflow.InflowConnectivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class NetworkConnectivity(context: Context) : InflowConnectivity {

    private val manager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val state = MutableStateFlow(manager.isConnected())

    override val connected = state.asStateFlow()

    init {
        val params = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(
            params,
            object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    state.tryEmit(manager.isConnected())
                }

                override fun onAvailable(network: Network) {
                    state.tryEmit(manager.isConnected())
                }
            }
        )
    }

    // Should come up with a better solution, but this one seems to work fine so far.
    @Suppress("DEPRECATION")
    private fun ConnectivityManager.isConnected() = activeNetworkInfo?.isConnected ?: false

}

@Volatile
private var Network: InflowConnectivity? = null

/**
 * Connectivity provider that listens for an active network interface that can reach
 * the internet.
 */
@Suppress("FunctionName") // Mimicking sealed class but allowing extensions
fun InflowConnectivity.Companion.Network(appContext: Context): InflowConnectivity {
    // Only creating the instance once, to not subscribe to network updates several times
    return Network ?: synchronized(this) {
        Network ?: NetworkConnectivity(appContext).also { Network = it }
    }
}
