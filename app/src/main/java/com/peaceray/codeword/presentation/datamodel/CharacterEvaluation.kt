package com.peaceray.codeword.presentation.datamodel

import com.peaceray.codeword.game.data.Constraint.MarkupType

data class CharacterEvaluation(val character: Char, val maxCount: Int, val markup: MarkupType? = null)