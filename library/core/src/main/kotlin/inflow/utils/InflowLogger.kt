package inflow.utils

import inflow.InflowConfig

public object InflowLogger {

    /**
     * Enables verbose logging of various Inflow events.
     * Use [InflowConfig.logId] to distinguish events from different Inflows.
     */
    public var verbose: Boolean = false

    /**
     * Logger implementation.
     */
    public var logger: (id: String, msg: String) -> Unit = { id, msg ->
        println("inflow: ${now() % 1_000_000} | $id | $msg")
    }

}

@JvmSynthetic // Avoiding coverage report issues
internal inline fun log(id: String, crossinline msg: () -> String) {
    if (InflowLogger.verbose) InflowLogger.logger(id, msg())
}
