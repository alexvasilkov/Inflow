package com.alexvasilkov.inflow.ui.questions

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.data.StackExchangeRepo
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import com.alexvasilkov.inflow.ui.ext.stateField
import inflow.Inflow
import inflow.cache
import inflow.loading
import inflow.unhandledError
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
    private val questionsQuery = searchChanges
        .debounce(400L).onStart { emit(searchQuery) } // De-bouncing but starting with initial query
        .combine(tagChanges, ::QuestionsQuery)

    private val questions = repo.searchQuestions(questionsQuery)

    // While collecting this flow the loading and refresh API calls will be made automatically
    val list: Flow<List<Question>?> = questions.data()

    val state: Flow<State> = questions.state()

    val errorMessage: Flow<Throwable> = questions.unhandledError()

    fun refresh() = questions.refresh()


    private fun <T> Inflow<List<T>?>.state() = combine(cache(), loading(), error(), ::newState)
        // The new data can arrive earlier than `loading = false` event, we want the UI to skip
        // showing unnecessary "refreshState" right after "loadingState" in this case
        .distinctUntilChanged { old, new -> old.loading && new.refresh }

    private fun newState(list: List<*>?, loading: Boolean, error: Throwable?): State = when {
        list == null -> State(loading = error == null, error = error != null)
        list.isEmpty() -> State(loading = loading, empty = !loading)
        else -> State(refresh = loading)
    }

    class State(
        val empty: Boolean = false,
        val loading: Boolean = false,
        val refresh: Boolean = false,
        val error: Boolean = false
    )

}
