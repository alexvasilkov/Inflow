package com.alexvasilkov.inflow.se.ui.list

import androidx.lifecycle.ViewModel
import com.alexvasilkov.inflow.ext.stateField
import com.alexvasilkov.inflow.se.data.StackExchangeRepo
import com.alexvasilkov.inflow.se.model.Question
import com.alexvasilkov.inflow.se.model.QuestionsQuery
import inflow.cache
import inflow.loading
import inflow.unhandledError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.map
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

    private val state: Flow<State> = combine(questions.cache(), questions.loading(), ::State)
        // Avoiding initial state, not an error yet
        .dropWhile { !it.loaded && !it.loading }
        // The new cache can arrive earlier than `loading = false` event, we want the UI to skip
        // showing unnecessary "refreshState" right after "loadingState" in this case
        .distinctUntilChanged { old, new -> old.loading && new.loading && old.empty && !new.empty }

    val emptyState: Flow<Boolean> = state.map { it.loaded && it.empty && !it.loading }

    val loadingState: Flow<Boolean> = state.map { it.empty && it.loading }

    val refreshState: Flow<Boolean> = state.map { !it.empty && it.loading }

    val errorState: Flow<Boolean> = state.map { !it.loaded && !it.loading }

    val errorMessage: Flow<Throwable> = questions.unhandledError()

    fun refresh() = questions.refresh()


    private class State(list: List<Question>?, val loading: Boolean) {
        val loaded: Boolean = list != null
        val empty: Boolean = list.isNullOrEmpty()
    }

}
