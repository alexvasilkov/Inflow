package com.alexvasilkov.inflow.ui.questions

import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.alexvasilkov.inflow.databinding.ListErrorItemBinding
import com.alexvasilkov.inflow.databinding.ListLoadingItemBinding
import com.alexvasilkov.inflow.databinding.SeQuestionItemBinding
import com.alexvasilkov.inflow.databinding.SeQuestionTagBinding
import com.alexvasilkov.inflow.model.Question
import com.alexvasilkov.inflow.model.QuestionsQuery
import com.alexvasilkov.inflow.ui.ext.inflate
import com.alexvasilkov.inflow.ui.ext.openUrl
import com.alexvasilkov.inflow.ui.questions.QuestionsListViewModel.UiState
import java.text.DecimalFormat

class QuestionsListAdapter(
    private val loadNext: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val typeItem = 0
    private val typeLoading = 1
    private val typeError = 2

    private val loadingOffset = 10

    private var state: UiState = UiState(emptyList(), QuestionsQuery("", ""))
    private var list: List<Question> = emptyList()

    fun setState(newState: UiState) {
        val old = list
        val oldState = state
        val new = newState.list

        list = new
        state = newState

        if (newState.query != oldState.query) {
            notifyDataSetChanged()
            loadNext()
            return
        }

        if (old.isEmpty()) {
            notifyDataSetChanged()
            return
        }

        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = old.size

            override fun getNewListSize() = new.size

            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                old[oldPos].id == new[newPos].id

            override fun areContentsTheSame(oldPos: Int, newPost: Int) =
                old[oldPos].lastActivity == new[newPost].lastActivity
        })
        diff.dispatchUpdatesTo(this)

        if (oldState.hasNext && newState.hasNext) {
            notifyItemChanged(new.size)
        } else if (!oldState.hasNext && newState.hasNext) {
            notifyItemInserted(new.size)
        } else if (oldState.hasNext && !newState.hasNext) {
            notifyItemRemoved(new.size)
        }
    }

    override fun getItemCount() = list.size + if (state.hasNext) 1 else 0

    override fun getItemViewType(pos: Int) = when {
        pos < list.size -> typeItem
        pos == list.size && state.hasNext -> if (state.errorNext) typeError else typeLoading
        else -> throw IllegalArgumentException()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = when (viewType) {
        typeItem -> ItemHolder(parent.inflate(SeQuestionItemBinding::inflate))
        typeLoading -> LoadingHolder(parent.inflate(ListLoadingItemBinding::inflate))
        typeError -> ErrorHolder(parent.inflate(ListErrorItemBinding::inflate))
        else -> throw IllegalArgumentException()
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemHolder) holder.bind(list[position])
    }


    // TODO: Abstract to a helper / parent class?
    private val scrollListener: RecyclerView.OnScrollListener =
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                loadNextItemsIfNeeded(recyclerView)
            }
        }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        recyclerView.addOnScrollListener(scrollListener)
        loadNextItemsIfNeeded(recyclerView)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        recyclerView.removeOnScrollListener(scrollListener)
    }

    private fun loadNextItemsIfNeeded(recycler: RecyclerView) {
        // TODO: Add option to require manual next page load triggering
        if (state.hasNext) { // TODO: Avoid excessive calls
            val lastVisibleChild = recycler.getChildAt(recycler.childCount - 1)
            val lastVisiblePos = recycler.getChildAdapterPosition(lastVisibleChild)
            val total = itemCount
            if (lastVisiblePos >= total - loadingOffset) {
                // We need to use runnable, since recycler view does not like when we notify about
                // changes during scroll callback.
                recycler.post { loadNext() }
            }
        }
    }


    class ItemHolder(
        private val views: SeQuestionItemBinding
    ) : RecyclerView.ViewHolder(views.root) {

        private lateinit var question: Question

        init {
            views.root.setOnClickListener { views.root.context.openUrl(question.link) }
        }

        fun bind(question: Question) {
            this.question = question

            views.score.text = question.score.asScore()
            views.views.text = question.views.asViews()
            views.answers.text = question.answers.toString()
            views.answered.isVisible = question.isAnswered
            views.nonAnswered.isVisible = !question.isAnswered
            views.title.text = HtmlCompat.fromHtml(question.title, 0)

            views.tags.removeAllViews()
            question.tags.forEach { tag ->
                val chip = views.tags.inflate(SeQuestionTagBinding::inflate, attachToParent = true)
                chip.root.text = tag
            }
        }

        companion object {
            private val scoreFormatter = DecimalFormat("+#,###;-#")

            private fun Int.asScore() = if (this == 0) "0" else scoreFormatter.format(this)

            private fun Long.asViews(): String = when {
                this < 1_000L -> "$this"
                this < 1_000_000L -> "${String.format("%.1f", this / 1_000.0)}k"
                else -> "${String.format("%.1f", this / 1_000_000.0)}m"
            }
        }
    }

    class LoadingHolder(views: ListLoadingItemBinding) : RecyclerView.ViewHolder(views.root)

    inner class ErrorHolder(views: ListErrorItemBinding) : RecyclerView.ViewHolder(views.root) {
        init {
            views.root.setOnClickListener { loadNext() }
        }
    }

}
