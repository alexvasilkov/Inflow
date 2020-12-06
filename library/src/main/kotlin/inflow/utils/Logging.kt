package inflow.utils

import android.util.Log

var inflowVerbose: Boolean = false

internal var logInTest = false
internal var lastLogMessage: String? = null; private set

internal fun log(id: Any, msg: () -> String) {
    if (inflowVerbose) {
        val message = msg()
        if (logInTest) {
            println("inflow: ${now() % 1_000_000} | $id | $message")
            lastLogMessage = message
        } else {
            Log.d("inflow", "$id | $message")
        }
    }
}
