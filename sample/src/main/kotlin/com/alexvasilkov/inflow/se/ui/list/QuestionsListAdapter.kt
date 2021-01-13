package com.alexvasilkov.inflow.se.ui.list

import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.alexvasilkov.inflow.databinding.SeQuestionItemBinding
import com.alexvasilkov.inflow.databinding.SeQuestionTagBinding
import com.alexvasilkov.inflow.ext.inflate
import com.alexvasilkov.inflow.ext.openUrl
import com.alexvasilkov.inflow.se.model.Question
import java.text.DecimalFormat

class QuestionsListAdapter : RecyclerView.Adapter<QuestionsListAdapter.Holder>() {

    private var list: List<Question>? = null

    fun setList(list: List<Question>?) {
        this.list = list
        notifyDataSetChanged()
    }

    override fun getItemCount() = list?.size ?: 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(parent.inflate(SeQuestionItemBinding::inflate))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(list!![position])


    class Holder(
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

}
