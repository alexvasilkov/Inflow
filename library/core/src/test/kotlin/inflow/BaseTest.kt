package inflow

import inflow.utils.inflowVerbose
import kotlin.test.BeforeTest

const val STRESS_TAG = "stress"
const val STRESS_TIMEOUT = 20L
const val STRESS_RUNS = 6_000

open class BaseTest {

    val logId = "TEST"

    @BeforeTest
    fun setup() {
        inflowVerbose = true
    }

}
