package com.alexvasilkov.inflow.se.ui.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import org.koin.android.viewmodel.ext.android.viewModel

class AuthResultActivity : AppCompatActivity() {

    private val viewModel: AuthResultViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = requireNotNull(intent.dataString)
        viewModel.handlerRedirectUrl(url)
        finish()
    }

}
