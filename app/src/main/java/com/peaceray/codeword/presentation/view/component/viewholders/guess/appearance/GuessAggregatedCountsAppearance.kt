package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout

open class GuessAggregatedCountsAppearance(context: Context, protected val layout: GuessAggregateConstraintCellLayout): GuessAggregatedAppearance {

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
            swatch.evaluation.color(markup)
        }
    }

    override val hasStableDimensions = true

    override fun getPipSize(guess: Guess, markup: Constraint.MarkupType?): Float {
        return layout.pipSize
    }

    override fun getPipMargin(guess: Guess, markup: Constraint.MarkupType?): Float {
        return layout.pipMargins
    }

    override fun getPipStrokeWidth(guess: Guess, markup: Constraint.MarkupType?): Int {
        return layout.pipStrokeWidth.toInt()
    }

    override fun getPipCornerRadius(guess: Guess, markup: Constraint.MarkupType?): Float {
        return layout.size / 2.0f
    }

    override fun getPipElevation(guess: Guess, markup: Constraint.MarkupType?): Float {
        return if (!guess.isEvaluation) 0.0f else layout.pipElevation
    }

    override fun getTotalCount(guess: Guess) = getExactCount(guess) + getIncludedCount(guess)
    override fun getExactCount(guess: Guess) = guess.constraint?.exact ?: 0
    override fun getIncludedCount(guess: Guess) = guess.constraint?.included ?: 0
}