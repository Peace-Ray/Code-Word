package com.peaceray.codeword.presentation.view.activities

import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatActivity
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
abstract class CodeWordActivity : AppCompatActivity() {

    @Inject lateinit var colorSwatchManager: ColorSwatchManager
    @StyleRes var currentTheme: Int = 0

    private var alive: Boolean = false
        set(value) {
            field = value
            Timber.v("alive: set to ${value}")
        }

    val isAlive get() = alive

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentTheme = colorSwatchManager.colorSwatch.theme
        setTheme(currentTheme)

        alive = true
    }

    override fun onResume() {
        super.onResume()
        if (currentTheme != colorSwatchManager.colorSwatch.theme) {
            recreate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        alive = false
    }
}