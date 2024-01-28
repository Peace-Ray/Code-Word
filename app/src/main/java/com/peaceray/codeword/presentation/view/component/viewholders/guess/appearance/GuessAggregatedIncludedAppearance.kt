package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess

class GuessAggregatedIncludedAppearance(context: Context): GuessAggregatedCountsAppearance(context) {
    override fun getExactCount(guess: Guess) = 0
    override fun getIncludedCount(guess: Guess) = guess.constraint?.let { it.exact + it.included } ?: 0
}