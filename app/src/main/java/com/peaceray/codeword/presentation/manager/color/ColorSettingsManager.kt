package com.peaceray.codeword.presentation.manager.color

import com.peaceray.codeword.presentation.datamodel.CodeColorScheme
import com.peaceray.codeword.presentation.datamodel.EvaluationColorScheme

interface ColorSettingsManager {

    //region Night Mode
    //---------------------------------------------------------------------------------------------
    enum class NightMode {
        YES,
        NO,
        DEVICE;
    }

    var nightModeSetting: NightMode
    val nightMode: Boolean
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Color Scheme
    //---------------------------------------------------------------------------------------------
    var evaluationColorScheme: EvaluationColorScheme
    var codeColorScheme: CodeColorScheme
    var codeColorsInverted: Boolean
    //---------------------------------------------------------------------------------------------
    //endregion

}