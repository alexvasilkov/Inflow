package com.alexvasilkov.inflow.ext

import android.view.LayoutInflater
import android.view.ViewGroup

inline fun <T> ViewGroup.inflate(
    crossinline action: (inflater: LayoutInflater, parent: ViewGroup?, attach: Boolean) -> T,
    attachToParent: Boolean = false
): T = action(LayoutInflater.from(context), this, attachToParent)
