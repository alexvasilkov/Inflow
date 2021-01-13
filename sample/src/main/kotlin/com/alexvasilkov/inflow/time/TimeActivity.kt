package com.alexvasilkov.inflow.time

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.alexvasilkov.inflow.R
import com.alexvasilkov.inflow.databinding.TimeScreenBinding
import com.alexvasilkov.inflow.ext.now
import com.alexvasilkov.inflow.ext.whileStarted
import inflow.ExpiresIn
import inflow.inflow
import inflow.loading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

class TimeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = TimeScreenBinding.inflate(layoutInflater)
        setContentView(views.root)
        setTitle(R.string.time_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // "Loading" current time every second. Using small delay to observe loading state.
        // (Updating time only once per second is not really accurate, but it's fine for the demo).
        val time = inflow<Long> {
            data(initial = now()) { delay(200L); now() }
            expiration(ExpiresIn(1_000L - 200L) { it })
            loadDispatcher(Dispatchers.Main.immediate)
        }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        time.data().whileStarted(this) { views.time.text = formatter.format(it) }
        time.loading().whileStarted(this) { views.time.animate().alpha(if (it) 0.4f else 1f) }
    }

}