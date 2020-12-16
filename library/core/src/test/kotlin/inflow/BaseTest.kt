package inflow

import inflow.utils.inflowVerbose
import org.junit.Before

open class BaseTest {

    val logId = "TEST"

    @Before
    fun setup() {
        inflowVerbose = true
    }

}
