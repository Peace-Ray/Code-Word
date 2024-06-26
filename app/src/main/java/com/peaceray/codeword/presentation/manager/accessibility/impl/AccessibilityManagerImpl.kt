package com.peaceray.codeword.presentation.manager.accessibility.impl

import android.content.Context
import android.content.res.Resources
import androidx.core.content.edit
import com.peaceray.codeword.R
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.manager.preferences.AbstractPreferencesManager
import com.peaceray.codeword.presentation.manager.accessibility.AccessibilityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityManagerImpl @Inject constructor(
    @ForApplication context: Context,
    @ForApplication resources: Resources
): AccessibilityManager, AbstractPreferencesManager(context, resources) {

    //region AccessibilityManager Settings
    //---------------------------------------------------------------------------------------------
    override var isHardwareKeyboardAllowed: Boolean
        get() = preferences.getBoolean(R.string.pref_key_hardware_keyboard, true)
        set(value) {
            preferences.edit { putBoolean(R.string.pref_key_hardware_keyboard, value) }
        }
    //---------------------------------------------------------------------------------------------
    //endregion

}