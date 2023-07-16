@file:Suppress(
    "NO_EXPLICIT_VISIBILITY_IN_API_MODE_WARNING", "NO_EXPLICIT_RETURN_TYPE_IN_API_MODE_WARNING"
)

package inflow.paging

import inflow.Inflow
import inflow.State
import inflow.base.runTest
import inflow.base.testDispatcher
import inflow.cached
import inflow.paging.internal.PagedImpl
import inflow.utils.InflowLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import java.lang.reflect.Method


private const val printSuccessCases = false


abstract class TestSuite {
    fun cases(): List<PagerTestCase> = this::class.java.methods
        .filter { it.returnType == Expect::class.java }
        .map { PagerTestCase(this, it) }

    class Expect(vararg expect: Int, loads: Int = -1, ignore: Boolean = false) :
        PagerTestResult(expect.map { Item(it, it) }, loads, ignore)

    companion object {
        const val more = Int.MAX_VALUE
    }
}

class PagerTestCase(obj: Any, method: Method) {
    val name = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
    val pageSize: Int
    val steps: List<PagerTestStep>
    val result: PagerTestResult

    init {
        // Getting page size at first
        val pageSizeMatch = "^!(\\d)\\s*".toRegex().find(name)
        pageSize = pageSizeMatch?.groups?.get(1)?.value?.toInt() ?: 3

        // Parsing method name into sequence of test steps
        val start = (pageSizeMatch?.range?.endInclusive ?: -1) + 1
        val parts = name.substring(start).split(" - ")

        val stepsResult = mutableListOf<PagerTestStep>()
        for (part in parts) {
            stepsResult += when {
                part.startsWith("next") ->
                    PagerTestStep.LoadNext(part.removePrefix("next").parseAsItems())
                part.startsWith("refresh") ->
                    PagerTestStep.Refresh(part.removePrefix("refresh").parseAsItems())
                part == "none" -> continue
                else -> throw IllegalArgumentException()
            }
        }
        steps = stepsResult

        // Getting expected result
        result = method.invoke(obj) as PagerTestResult
    }
}

private fun String.parseAsItems(): List<Item> {
    // Parsing new remote list
    require(this.startsWith("(") && this.endsWith(")")) { "Should include () but was '$this'" }

    val stripped = this.substring(1, this.length - 1)
    return if (stripped.isBlank()) {
        emptyList()
    } else {
        val remoteItems = stripped.split(", ").map {
            val itemParts = it.split("^")
            val id = itemParts[0].toInt()
            val order = if (itemParts.size > 1) itemParts[1].toInt() else id
            Item(id, order)
        }
        remoteItems
    }
}

sealed class PagerTestStep {
    class LoadNext(val items: List<Item>) : PagerTestStep()
    class Refresh(val items: List<Item>) : PagerTestStep()
}

abstract class PagerTestResult(
    val expect: List<Item>,
    val loads: Int,
    val ignore: Boolean
)

class Item(val id: Int, val order: Int) : Comparable<Item> {
    override fun equals(other: Any?) = id == (other as? Item)?.id
    override fun hashCode() = id
    override fun compareTo(other: Item) = order.compareTo(other.order)
    override fun toString() = if (id == order) "$id" else "$id^$order"
}

abstract class PagerTestRemote {
    lateinit var result: List<Item>
    var loads = 0

    abstract fun load(params: PageParams<Int>): PageResult<Item, Int>

    fun loadInternal(params: PageParams<Int>): PageResult<Item, Int> {
        loads++
        return load(params)
    }
}


@ExperimentalCoroutinesApi
fun runPagerTests(
    suite: TestSuite,
    remote: () -> PagerTestRemote,
    config: PagerConfig<Item, Int>.() -> Unit = {}
) = runTest {
    val wasVerbose = InflowLogger.verbose
    InflowLogger.verbose = false // Avoiding spamming in logs

    val cases = suite.cases()
    var errors = 0
    var total = 0

    for (case in cases) {
        val remoteInstance = remote()
        val inflow = inflowPaged<Item> {
            pager<Int> {
                pageSize(case.pageSize)
                loader { _, params -> remoteInstance.loadInternal(params) }
                config()
            }
            dispatcher(testDispatcher)
        }
        val result = runPagerTestSteps(case, inflow, remoteInstance)
        checkErrors(inflow)
        total++
        if (!result) errors++
    }

    InflowLogger.verbose = wasVerbose

    if (errors > 0) throw AssertionError("$errors of $total cases failed, see console output")
}

private suspend fun runPagerTestSteps(
    case: PagerTestCase,
    inflow: Inflow<Paged<Item>>,
    remote: PagerTestRemote
): Boolean {
    if (case.result.ignore) {
        if (printSuccessCases) println("\uD83D\uDCA4 ${case.name}") // Z-z-z
        return true
    }

    for (step in case.steps) {
        when (step) {
            is PagerTestStep.LoadNext -> {
                remote.result = step.items
                inflow.loadNext()
            }
            is PagerTestStep.Refresh -> {
                remote.result = step.items
                inflow.refresh()
            }
        }
    }

    val actual = inflow.cached()
    val history = StringBuilder("${case.name} => ${actual.str()}")

    val result = case.result.expect
    val expect = if (result.lastOrNull()?.id == TestSuite.more) {
        PagedImpl(result.subList(0, result.size - 1), true)
    } else {
        PagedImpl(result, false)
    }

    var success = true

    if (!case.result.ignore) {
        if (expect.items != actual.items || expect.hasNext != actual.hasNext) {
            history.append(" != ${expect.str()}")
            success = false
        }
        if (case.result.loads >= 0 && remote.loads != case.result.loads) {
            history.append(" => ExpectLoads(${remote.loads} != ${case.result.loads})")
            success = false
        }
    }

    when {
        !success -> println("\uD83D\uDCA5 $history") // Explosion
        printSuccessCases -> println("✔️ $history")
    }
    return success
}

private suspend fun checkErrors(inflow: Inflow<Paged<Item>>) {
    val loadNextState = inflow.loadNextState().first()
    if (loadNextState is State.Idle.Error)
        println("\uD83D\uDCA5 Load next error: ${loadNextState.throwable}")

    val refreshState = inflow.refreshState().first()
    if (refreshState is State.Idle.Error)
        println("\uD83D\uDCA5 Refresh error: ${refreshState.throwable}")
}

private fun List<Item>.str() = joinToString(prefix = "[", postfix = "]")

private fun Paged<Item>.str() = "Paged(${items.str()}, $hasNext)"
