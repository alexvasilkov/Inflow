package com.alexvasilkov.inflow

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import inflow.ExpiresIn
import inflow.android.InflowAndroid
import inflow.asInflow
import inflow.inflow
import inflow.utils.inflowVerbose
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
class TestActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.test_activity)
        val timeText: TextView = findViewById(R.id.time)
        val progressText: TextView = findViewById(R.id.progress)
        val blockedView: View = findViewById(R.id.blocked)

        blockedView.setOnClickListener { Toast.makeText(this, "Nope!", Toast.LENGTH_SHORT).show() }

        InflowAndroid.init(applicationContext)
        inflowVerbose = true

        val configs = inflow<Config> {
            var extraDays = 0L
            data(initial = Config(extraDays)) { Config(++extraDays) }
            expiration(ExpiresIn(3_000L) { it.loadedAt })
        }

        val time = configs.data().asInflow<Config, Time?> {
            builder { config ->
                data(initial = null) {
                    delay(200L)
                    Time(now() + TimeUnit.DAYS.toMillis(config.extraDays))
                }
                expiration(ExpiresIn(1_000L - 200L) { it?.loadedAt ?: 0L })
            }
        }

        val formatter = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.ENGLISH)
        time.data()
            .filterNotNull()
            .onEach { timeText.text = formatter.format(it.time) }
            .launchIn(lifecycleScope)

        time.progress()
            .onEach { progressText.text = it::class.simpleName }
            .launchIn(lifecycleScope)
    }

    private data class Config(
        val extraDays: Long,
        val loadedAt: Long = now()
    )

    private data class Time(
        val time: Long,
        val loadedAt: Long = now()
    )

}

private fun now(): Long = System.currentTimeMillis()
