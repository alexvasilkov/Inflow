package com.alexvasilkov.inflow.ui.questions

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import com.alexvasilkov.inflow.ui.ext.stateField
import inflow.Inflow
import inflow.State
import inflow.State.Idle
import inflow.State.Loading
import inflow.map
import inflow.refreshError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
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

    private val questions = repo.searchQuestions(query).map { it?.items }

    // While collecting this flow the loading and refresh API calls will be made automatically
    val list: Flow<List<Question>?> = questions.data()

    val state: Flow<UiState> = questions.uiState()

    val errorMessage: Flow<Throwable> = questions.refreshError()

    fun refresh() = questions.refresh()


    private fun <T> Inflow<List<T>?>.uiState() = combine(cache(), refreshState(), ::newState)
        // The new data can arrive earlier than `loading = false` event, we want the UI to skip
        // showing unnecessary "refreshState" right after "loadingState" in this case
        .distinctUntilChanged { old, new -> old.loading && new.refresh }

    private fun newState(list: List<*>?, state: State): UiState = when {
        list == null -> UiState(loading = state is Loading, error = state is Idle.Error)
        list.isEmpty() -> UiState(loading = state is Loading, empty = state !is Loading)
        else -> UiState(refresh = state is Loading)
    }

    class UiState(
        val empty: Boolean = false,
        val loading: Boolean = false,
        val refresh: Boolean = false,
        val error: Boolean = false
    )

}
