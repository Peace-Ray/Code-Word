package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout

open class GuessAggregatedCountsPipAppearance(context: Context, protected val layout: GuessAggregateConstraintCellLayout): GuessAggregatedAppearance {

    override fun getColorFill(
        evaluation: GuessEvaluation?,
        markup: Constraint.MarkupType?,
        swatch: ColorSwatch
    ): Int {
        return if (evaluation == null) {
            swatch.container.background
        } else {
            swatch.evaluation.color(markup)
        }
    }

    override fun getColorStroke(
        evaluation: GuessEvaluation?,
        markup: Constraint.MarkupType?,
        swatch: ColorSwatch
    ): Int {
        return if (evaluation == null) {
            swatch.evaluation.untried
        } else {
            swatch.evaluation.color(markup)
        }
    }

    override val hasStableDimensions = true

    override fun getPipSize(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return layout.pipSize
    }

    override fun getPipMargin(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return layout.pipMargins
    }

    override fun getPipStrokeWidth(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Int {
        return layout.pipStrokeWidth.toInt()
    }

    override fun getPipCornerRadius(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return layout.size / 2.0f
    }

    override fun getPipElevation(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return if (evaluation == null) 0.0f else layout.pipElevation
    }
}