package com.alexvasilkov.inflow.ui.time

import android.os.Bundle
import com.alexvasilkov.inflow.R
import com.alexvasilkov.inflow.data.ext.now
import com.alexvasilkov.inflow.databinding.TimeScreenBinding
import com.alexvasilkov.inflow.ui.BaseActivity
import com.alexvasilkov.inflow.ui.ext.whileStarted
import inflow.Expires
import inflow.inflow
import inflow.loading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class TimeActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = TimeScreenBinding.inflate(layoutInflater)
        setContentView(views.root)
        setTitle(R.string.time_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        // "Loading" current time every second. Using small delay to observe loading state.
        // (Updating time only once per second is not really accurate, but it's fine for the demo).
        val time = inflow<Long> {
            data(initial = now()) { delay(250L); now() }
            expiration(Expires.after(1_000L - 250L) { it })
            loadDispatcher(Dispatchers.Main.immediate)
        }

        val formatter = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
        time.data().whileStarted(this) { views.time.text = formatter.format(it) }
        time.loading().whileStarted(this) { views.time.animate().alpha(if (it) 0f else 1f) }
    }

}
