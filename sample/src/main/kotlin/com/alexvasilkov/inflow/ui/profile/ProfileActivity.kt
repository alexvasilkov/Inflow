package com.alexvasilkov.inflow.ui.profile

import android.os.Bundle
import androidx.core.view.isVisible
import coil.load
import com.alexvasilkov.inflow.R
import com.alexvasilkov.inflow.databinding.SeProfileScreenBinding
import com.alexvasilkov.inflow.ui.BaseActivity
import com.alexvasilkov.inflow.ui.ext.openUrl
import com.alexvasilkov.inflow.ui.ext.toast
import com.alexvasilkov.inflow.ui.ext.whileStarted
import org.koin.android.viewmodel.ext.android.viewModel

class ProfileActivity : BaseActivity() {

    private val viewModel: ProfileViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val views = SeProfileScreenBinding.inflate(layoutInflater)
        setContentView(views.root)
        setTitle(R.string.se_profile_title)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        viewModel.authState.whileStarted(this) { loggedIn ->
            views.loginLayout.isVisible = !loggedIn
            views.profileLayout.isVisible = loggedIn
        }

        views.login.setOnClickListener { openUrl(viewModel.authUrl) }
        views.logout.setOnClickListener { viewModel.logout() }

        viewModel.profile.whileStarted(this) { profile ->
            views.name.text = profile?.name
            views.reputation.text = profile?.reputation?.toString()
            views.image.load(profile?.image) {
                placeholder(R.drawable.ic_profile_placeholder_96dp)
            }
        }

        viewModel.loading.whileStarted(this) { views.progress.isVisible = it }

        viewModel.error.whileStarted(this) { toast(R.string.se_profile_error) }
    }

}
