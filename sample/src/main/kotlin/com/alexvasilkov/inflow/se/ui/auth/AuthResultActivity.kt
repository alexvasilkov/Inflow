package com.alexvasilkov.inflow.se.ui.auth

import android.content.Intent
import android.os.Bundle
import com.alexvasilkov.inflow.base.BaseActivity
import com.alexvasilkov.inflow.se.ui.profile.ProfileActivity
import org.koin.android.viewmodel.ext.android.viewModel

class AuthResultActivity : BaseActivity() {

    private val viewModel: AuthResultViewModel by viewModel()

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
