package com.peaceray.codeword.utils.extensions

import android.content.res.Resources
import android.graphics.Rect
import android.view.View

fun View.isVisibleToUser(): Boolean {
    if (!isShown) {
        return false
    }
    val actualPosition = Rect()
    val isGlobalVisible = getGlobalVisibleRect(actualPosition)
    val screenWidth = Resources.getSystem().displayMetrics.widthPixels
    val screenHeight = Resources.getSystem().displayMetrics.heightPixels
    val screen = Rect(0, 0, screenWidth, screenHeight)
    return isGlobalVisible && Rect.intersects(actualPosition, screen)
}