@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow

import inflow.utils.inflowVerbose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.BeforeTest

const val STRESS_TAG = "stress"
const val STRESS_TIMEOUT = 30L
const val STRESS_RUNS = 10_000

open class BaseTest {

    val logId = "TEST"

    @BeforeTest
    fun setup() {
        inflowVerbose = true

        if (InflowConnectivity.Default == null) {
            InflowConnectivity.Default = object : InflowConnectivity {
                override val connected = MutableStateFlow(true)
            }
        }
    }

}
