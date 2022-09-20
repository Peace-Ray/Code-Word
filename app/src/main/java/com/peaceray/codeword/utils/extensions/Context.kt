package com.peaceray.codeword.utils.extensions

import android.R
import android.content.Context
import android.content.ContextWrapper
import android.util.TypedValue
import androidx.lifecycle.LifecycleOwner


fun Context.toLifecycleOwner(): LifecycleOwner? = when (this) {
    is LifecycleOwner -> this
    is ContextWrapper -> baseContext.toLifecycleOwner()
    else -> null
}

fun Context.getThemePrimaryColor(): Int {
    val value = TypedValue()
    theme.resolveAttribute(R.attr.colorPrimary, value, true)
    return value.data
}

fun Context.getThemeSecondaryColor(): Int {
    val value = TypedValue()
    theme.resolveAttribute(R.attr.colorSecondary, value, true)
    return value.data
}

fun Context.getThemeAccentColor(): Int {
    val value = TypedValue()
    theme.resolveAttribute(R.attr.colorAccent, value, true)
    return value.data
}