package com.alexvasilkov.inflow.ui.questions

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsData
import com.alexvasilkov.inflow.model.QuestionsQuery
import com.alexvasilkov.inflow.ui.ext.stateField
import inflow.Inflow
import inflow.State
import inflow.loadNext
import inflow.loadNextState
import inflow.refreshError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart

class QuestionsListViewModel(
    repo: StackExchangeRepo
) : ViewModel() {

    private val searchChanges = MutableStateFlow("")
    var searchQuery: String by stateField(searchChanges)

    val tags = listOf("android", "kotlin", "java")
    private val tagChanges = MutableStateFlow(tags[0])
    var searchTag: String by stateField(tagChanges)

    @Suppress("EXPERIMENTAL_API_USAGE") // Debounce is still experimental
    private val query = searchChanges
        .debounce(400L).onStart { emit(searchQuery) } // De-bouncing but starting with initial query
        .combine(tagChanges, ::QuestionsQuery)

    private val questions = repo.searchQuestions(query)

    val state: Flow<UiState> = questions.uiState()

    val errorMessage: Flow<Throwable> = questions.refreshError()

    fun refresh() = questions.refresh()

    fun loadNext() = questions.loadNext()


    private fun Inflow<QuestionsData>.uiState() =
        combine(data(), refreshState(), loadNextState(), ::newState)

    private fun newState(data: QuestionsData, refreshState: State, nextState: State): UiState {
        val empty = data.items.isEmpty()

        return UiState(
            list = data.items,
            query = data.query,
            empty = empty && !data.hasNext,
            refresh = refreshState is State.Loading && !(empty && data.hasNext),
            hasNext = data.hasNext,
            errorNext = nextState is State.Idle.Error
        )
    }

    class UiState(
        val list: List<Question>,
        val query: QuestionsQuery,
        val empty: Boolean = false,
        val refresh: Boolean = false,
        val hasNext: Boolean = false,
        val errorNext: Boolean = false
    )

}
