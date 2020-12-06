package inflow

import inflow.utils.inflowVerbose
import inflow.utils.logInTest
import org.junit.Before

open class BaseTest {

    @Before
    fun setup() {
        inflowVerbose = true
        logInTest = true
    }

}
