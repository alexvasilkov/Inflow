package com.alexvasilkov.inflow.se.ui.list

import android.os.Bundle
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alexvasilkov.inflow.R
import com.alexvasilkov.inflow.databinding.SeQuestionsListScreenBinding
import com.alexvasilkov.inflow.ext.dp
import com.alexvasilkov.inflow.ext.hideKeyboard
import com.alexvasilkov.inflow.ext.toast
import com.alexvasilkov.inflow.ext.whileStarted
import com.google.android.material.appbar.AppBarLayout
import org.koin.android.viewmodel.ext.android.viewModel


class QuestionsListActivity : AppCompatActivity() {

    private val viewModel: QuestionsListViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = SeQuestionsListScreenBinding.inflate(layoutInflater)
        setContentView(views.root)
        setTitle(R.string.se_title)

        setSupportActionBar(views.toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        val adapter = QuestionsListAdapter()
        views.list.layoutManager = LinearLayoutManager(this)
        views.list.adapter = adapter

        views.refreshLayout.setOnRefreshListener { viewModel.refresh() }
        views.errorRetry.setOnClickListener { viewModel.refresh() }

        views.initSearch()

        viewModel.list.whileStarted(this) { adapter.setList(it) }
        viewModel.emptyState.whileStarted(this) { views.emptyText.isVisible = it }
        viewModel.loadingState.whileStarted(this) { views.progress.isVisible = it }
        viewModel.refreshState.whileStarted(this) { views.refreshLayout.isRefreshing = it }
        viewModel.errorState.whileStarted(this) { views.errorLayout.isVisible = it }
        viewModel.errorMessage.whileStarted(this) { toast(R.string.se_search_error) }
    }


    private fun SeQuestionsListScreenBinding.initSearch() {
        // Setting up search query field
        search.setText(viewModel.searchQuery)
        search.addTextChangedListener { viewModel.searchQuery = it!!.trim().toString() }

        // Setting up tag field
        tagSearch.setText(viewModel.searchTag)
        val adapter = ArrayAdapter(root.context, R.layout.se_search_tag_item, viewModel.tags)
        tagSearch.setAdapter(adapter)
        tagSearch.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            viewModel.searchTag = adapter.getItem(position)!!
        }

        // Opening drop down popup on tag field click
        var dropDownShown = false
        tagSearch.setOnClickListener {
            if (dropDownShown) return@setOnClickListener
            dropDownShown = true
            hideKeyboard()
            tagSearch.showDropDown()
        }
        tagSearch.setOnDismissListener { tagSearch.postDelayed({ dropDownShown = false }, 100L) }
        tagSearch.dropDownVerticalOffset = dp(4f).toInt()

        // Removing focus from search field on scroll
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                hideKeyboard()
            }
        })

        // Animating search layout along with scroll
        appBar.addOnOffsetChangedListener(
            AppBarLayout.OnOffsetChangedListener { _, offset ->
                searchLayout.translationY = -offset * 0.5f
                searchLayout.alpha = 1f + offset * 2f / searchLayout.height
            }
        )
    }

}
