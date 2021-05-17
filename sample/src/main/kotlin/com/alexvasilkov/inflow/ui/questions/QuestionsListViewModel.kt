package com.alexvasilkov.inflow.ui.questions

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import com.alexvasilkov.inflow.model.QuestionsResult
import com.alexvasilkov.inflow.ui.ext.stateField
import inflow.InflowCombined
import inflow.State
import inflow.loadNext
import inflow.refreshError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample

@Suppress("EXPERIMENTAL_API_USAGE") // Debounce and sample are still experimental
class QuestionsListViewModel(
    repo: StackExchangeRepo
) : ViewModel() {

    private val searchChanges = MutableStateFlow("")
    var searchQuery: String by stateField(searchChanges)

    val tags = listOf("android", "kotlin", "java")
    private val tagChanges = MutableStateFlow(tags[0])
    var searchTag: String by stateField(tagChanges)

    private val query = searchChanges
        .debounce(400L)
        .onStart { emit(searchQuery) } // Debounce but always start with initial query
        .combine(tagChanges, ::QuestionsQuery)

    private val questions = repo.searchQuestions(query)

    val state: Flow<UiState> = questions.dataAndState()
        .map { it.toUiState() }
        .sample(50L) // Avoid updating the UI too often

    val errorMessage: Flow<Throwable> = questions.refreshError()

    fun refresh() = questions.refresh()

    fun loadNext() = questions.loadNext()


    private fun InflowCombined<QuestionsResult>.toUiState(): UiState {
        val empty = data.items.isEmpty()

        return UiState(
            query = data.query,
            list = data.items,
            empty = empty && !data.hasNext,
            refresh = refresh is State.Loading && !(empty && data.hasNext),
            hasNext = data.hasNext,
            errorNext = loadNext is State.Idle.Error
        )
    }

    class UiState(
        val query: QuestionsQuery,
        val list: List<Question>,
        val empty: Boolean = false,
        val refresh: Boolean = false,
        val hasNext: Boolean = false,
        val errorNext: Boolean = false
    )

}
