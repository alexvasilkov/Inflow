package com.alexvasilkov.inflow.ui.profile.auth

import android.content.Intent
import android.os.Bundle
import com.alexvasilkov.inflow.ui.BaseActivity
import com.alexvasilkov.inflow.ui.ext.viewModel
import com.alexvasilkov.inflow.ui.profile.ProfileActivity

class AuthResultActivity : BaseActivity() {

    private val viewModel by viewModel { AuthResultViewModel(auth) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = requireNotNull(intent.dataString)
        viewModel.handlerRedirectUrl(url)

        // Opening profile activity to ensure in-app Chrome screen is closed
        startActivity(
            Intent(this, ProfileActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
        finish()
    }

}
