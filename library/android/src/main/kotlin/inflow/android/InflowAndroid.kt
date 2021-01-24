package inflow.android

import android.content.Context
import android.util.Log
import inflow.Connectivity
import inflow.utils.inflowLogger

object InflowAndroid {

    fun init(context: Context) {
        inflowLogger = { id, msg -> Log.d("inflow", "$id | $msg") }
        Connectivity.default = Connectivity.network(context)
    }

}
