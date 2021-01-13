package com.alexvasilkov.inflow.ext

import android.content.Context
import android.net.Uri
import android.util.TypedValue
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.browser.customtabs.CustomTabsIntent

fun Context.openUrl(url: String): Unit =
    CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(this, Uri.parse(url))

fun Context.toast(@StringRes textId: Int): Unit =
    Toast.makeText(this, textId, Toast.LENGTH_SHORT).show()

fun Context.dp(value: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
