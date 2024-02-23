package com.peaceray.codeword.presentation.manager.accessibility.impl

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.peaceray.codeword.R
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.manager.accessibility.AccessibilityManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccessibilityManagerImpl @Inject constructor(
    @ForApplication private val context: Context,
    @ForApplication private val resources: Resources
): AccessibilityManager {

    //region SharedPreferences and Preference Mapping
    //---------------------------------------------------------------------------------------------
    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun SharedPreferences.getBoolean(keyId: Int, defaultValue: Boolean = false): Boolean {
        val key = resources.getString(keyId)
        return getBoolean(key, defaultValue)
    }

    private fun SharedPreferences.Editor.putBoolean(keyId: Int, value: Boolean) {
        putBoolean(resources.getString(keyId), value)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

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