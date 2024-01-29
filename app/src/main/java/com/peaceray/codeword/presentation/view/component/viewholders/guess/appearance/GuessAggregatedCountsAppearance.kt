package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess

open class GuessAggregatedCountsAppearance(context: Context): GuessAggregatedAppearance {

    private val dimenPipElevation: Float

    init {
        dimenPipElevation = context.resources.getDimension(R.dimen.aggregate_markup_pip_large_elevation)
    }

    override fun getColorFill(
        guess: Guess,
        markup: Constraint.MarkupType?,
        swatch: ColorSwatch
    ): Int {
        return if (!guess.isEvaluation) {
            swatch.container.background
        } else {
            swatch.evaluation.color(markup)
        }
    }

    override fun getColorStroke(
        guess: Guess,
        markup: Constraint.MarkupType?,
        swatch: ColorSwatch
    ): Int {
        return if (!guess.isEvaluation) {
            swatch.evaluation.untried
        } else {
            getColorFill(guess, markup, swatch)
        }
    }

    override fun getElevation(guess: Guess, markup: Constraint.MarkupType?): Float {
        return if (!guess.isEvaluation) 0.0f else dimenPipElevation
    }

    override fun getTotalCount(guess: Guess) = getExactCount(guess) + getIncludedCount(guess)
    override fun getExactCount(guess: Guess) = guess.constraint?.exact ?: 0
    override fun getIncludedCount(guess: Guess) = guess.constraint?.included ?: 0
}