package com.peaceray.codeword.presentation.manager.preferences

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import androidx.preference.PreferenceManager

abstract class AbstractPreferencesManager(
    protected val context: Context,
    protected val resources: Resources
) {
    
    //region SharedPreferences and Preference Mapping
    //---------------------------------------------------------------------------------------------
    protected val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    protected fun SharedPreferences.getString(keyId: Int, defaultValue: String? = null): String? {
        val key = resources.getString(keyId)
        return getString(key, defaultValue)
    }

    protected fun SharedPreferences.Editor.putString(keyId: Int, value: String?) {
        putString(resources.getString(keyId), value)
    }

    protected fun SharedPreferences.getBoolean(keyId: Int, defaultValue: Boolean = false): Boolean {
        val key = resources.getString(keyId)
        return getBoolean(key, defaultValue)
    }

    protected fun SharedPreferences.Editor.putBoolean(keyId: Int, value: Boolean) {
        putBoolean(resources.getString(keyId), value)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
    
}