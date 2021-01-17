package com.alexvasilkov.inflow

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.alexvasilkov.inflow.base.BaseActivity
import com.alexvasilkov.inflow.databinding.StartScreenBinding
import com.alexvasilkov.inflow.se.ui.list.QuestionsListActivity
import com.alexvasilkov.inflow.se.ui.profile.ProfileActivity
import com.alexvasilkov.inflow.time.TimeActivity

class StartActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = StartScreenBinding.inflate(layoutInflater)
        setContentView(views.root)

        views.stackExchangeSearch.openOnClick(QuestionsListActivity::class.java)
        views.stackExchangeProfile.openOnClick(ProfileActivity::class.java)
        views.time.openOnClick(TimeActivity::class.java)
    }

    private fun View.openOnClick(cl: Class<out Activity>) =
        setOnClickListener { startActivity(Intent(context, cl)) }

}
