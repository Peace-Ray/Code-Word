package com.peaceray.codeword.presentation.manager.privacy.impl

import android.content.Context
import android.content.res.Resources
import androidx.core.content.edit
import com.peaceray.codeword.R
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.manager.preferences.AbstractPreferencesManager
import com.peaceray.codeword.presentation.manager.privacy.PrivacyManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrivacyManagerImpl @Inject constructor(
    @ForApplication context: Context,
    @ForApplication resources: Resources
): PrivacyManager, AbstractPreferencesManager(context, resources) {

    //region Update Checking
    //---------------------------------------------------------------------------------------------

    override var isCheckingForUpdates: Boolean
        get() = preferences.getBoolean(R.string.pref_key_check_for_updates, true)
        set(value) {
            preferences.edit { putBoolean(R.string.pref_key_check_for_updates, value) }
        }

    //---------------------------------------------------------------------------------------------
    //endregion

}