package com.peaceray.codeword.presentation.manager.color

import com.peaceray.codeword.presentation.datamodel.CodeColorScheme
import com.peaceray.codeword.presentation.datamodel.EvaluationColorScheme

/**
 * Direct controls and access to the current color scheme settings
 * on the device. Does not provide Themes or ColorSwatches.
 */
interface ColorSettingsManager {

    //region Night Mode
    //---------------------------------------------------------------------------------------------
    enum class DarkModeSetting {
        YES,
        NO,
        DEVICE;
    }

    val darkModeSetting: DarkModeSetting
    val darkMode: Boolean
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Color Scheme
    //---------------------------------------------------------------------------------------------
    var evaluationColorScheme: EvaluationColorScheme
    var evaluationColorsInverted: Boolean
    var codeColorScheme: CodeColorScheme
    var codeColorsInverted: Boolean
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Updates
    //---------------------------------------------------------------------------------------------
    /**
     * It's possible that colors have been updated at an alternative source. Reload from
     * that source, potentially updating property values.
     */
    fun reloadColors()
    //---------------------------------------------------------------------------------------------
    //endregion

}