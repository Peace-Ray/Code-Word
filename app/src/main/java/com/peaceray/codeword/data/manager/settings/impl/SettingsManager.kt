package com.peaceray.codeword.data.manager.settings.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.peaceray.codeword.data.manager.settings.BotSettingsManager
import com.peaceray.codeword.glue.ForApplication
import javax.inject.Inject

class SettingsManager @Inject constructor(
    @ForApplication private val context: Context
): BotSettingsManager {

    //region SharedPreferences
    //---------------------------------------------------------------------------------------------
    private companion object {
        const val PREFS_FILENAME = "SETTINGS_MANAGER_PREFERENCES"
        // bot strengths
        const val KEY_SOLVER_STRENGTH = "SOLVER_STRENGTH"
        const val KEY_CHEATER_STRENGTH = "CHEATER_STRENGTH"
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Bot Settings
    //---------------------------------------------------------------------------------------------
    override var solverStrength: Float
        get() = preferences.getFloat(KEY_SOLVER_STRENGTH, 0.3f)
        set(value) {
            if (value < 0 || value > 1) {
                throw IllegalArgumentException("Solver Strength must be in [0, 1]")
            }
            preferences.edit { putFloat(KEY_SOLVER_STRENGTH, value) }
        }

    override var cheaterStrength: Float
        get() = preferences.getFloat(KEY_CHEATER_STRENGTH, 0.3f)
        set(value) {
            if (value < 0 || value > 1) {
                throw IllegalArgumentException("Cheater Strength must be in [0, 1]")
            }
            preferences.edit { putFloat(KEY_CHEATER_STRENGTH, value) }
        }
    //---------------------------------------------------------------------------------------------
    //endregion

}