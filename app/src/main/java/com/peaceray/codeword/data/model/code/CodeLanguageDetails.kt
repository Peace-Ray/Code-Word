package com.peaceray.codeword.data.model.code

import com.peaceray.codeword.game.data.ConstraintPolicy

data class CodeLanguageDetails (
    val language: CodeLanguage,
    val evaluation: ConstraintPolicy,
    val hardModeConstraint: ConstraintPolicy?,
    val characters: List<Char>,
    val isEnumeration: Boolean,
    val codeLengthRecommended: Int,
    val codeCharactersRecommended: Int,
    val codeLengthsSupported: List<Int>,
    val codeCharactersSupported: List<Int>,
    val hardModeSupported: Boolean
)