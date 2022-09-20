package com.peaceray.codeword

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.peaceray.codeword.presentation.manager.color.ColorSettingsManager
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class CodeWordApplication: Application() {
    @Inject lateinit var colorSettingsManager: ColorSettingsManager

    override fun onCreate() {
        super.onCreate()

        // setup Timber logging
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())

        // set dark mode
        Timber.d("ColorManager says darkModeSetting is ${colorSettingsManager.darkModeSetting}")
        when(colorSettingsManager.darkModeSetting) {
            ColorSettingsManager.DarkModeSetting.YES -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ColorSettingsManager.DarkModeSetting.NO -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ColorSettingsManager.DarkModeSetting.DEVICE -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}