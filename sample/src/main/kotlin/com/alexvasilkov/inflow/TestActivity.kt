package com.alexvasilkov.inflow

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import inflow.ExpiresIn
import inflow.inflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TestActivity : AppCompatActivity() {

    private lateinit var text: TextView

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        text = TextView(this)
        text.gravity = Gravity.CENTER
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        text.setOnClickListener { Toast.makeText(this, "Clicked", Toast.LENGTH_SHORT).show() }
        setContentView(text, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        val timer = inflow {
            cacheInMemory(initialValue = 0L)
            cacheExpiration(ExpiresIn(duration = 1000L, loadedAt = { this }))
            loader { System.currentTimeMillis() }
        }

        lifecycleScope.launch {
            val formatter = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
            timer.data().collect {
                text.text = formatter.format(it)
            }
        }
        lifecycleScope.launch {
            while (true) {
                delay(200L)
                text.text = ": ${text.text} :"
            }
        }
    }

}
