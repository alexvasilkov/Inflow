package inflow.utils

var inflowVerbose: Boolean = false
var inflowLogger: (id: String, msg: String) -> Unit = { id, msg ->
    println("inflow: ${now() % 1_000_000} | $id | $msg")
}

internal fun log(id: String, msg: () -> String) {
    if (inflowVerbose) inflowLogger(id, msg())
}
