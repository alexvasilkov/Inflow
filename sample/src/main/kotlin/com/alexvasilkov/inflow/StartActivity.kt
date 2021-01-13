package com.alexvasilkov.inflow

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alexvasilkov.inflow.databinding.StartScreenBinding
import com.alexvasilkov.inflow.se.ui.list.QuestionsListActivity
import com.alexvasilkov.inflow.time.TimeActivity

class StartActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = StartScreenBinding.inflate(layoutInflater)
        setContentView(views.root)

        views.stackExchange.setOnClickListener {
            startActivity(Intent(this, QuestionsListActivity::class.java))
        }
        views.time.setOnClickListener {
            startActivity(Intent(this, TimeActivity::class.java))
        }
    }

}
