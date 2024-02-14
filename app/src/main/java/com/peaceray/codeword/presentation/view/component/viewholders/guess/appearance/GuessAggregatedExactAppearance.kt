package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout

open class GuessAggregatedExactAppearance(context: Context, val layout: GuessAggregateConstraintCellLayout): GuessAggregatedAppearance {
    override fun getColorFill(
        evaluation: GuessEvaluation?,
        markup: Constraint.MarkupType?,
        swatch: ColorSwatch
    ): Int {
        return swatch.container.background
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
        return layout.donutSize
    }

    override fun getPipMargin(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return layout.donutMargins
    }

    override fun getPipStrokeWidth(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Int {
        return layout.donutStrokeWidth.toInt()
    }

    override fun getPipCornerRadius(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return layout.donutSize / 2.0f
    }

    override fun getPipElevation(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float {
        return if (evaluation == null) 0.0f else layout.donutElevation
    }

    override fun getTotalCount(evaluation: GuessEvaluation?) = getExactCount(evaluation) + getIncludedCount(evaluation)
    override fun getExactCount(evaluation: GuessEvaluation?) = evaluation?.exact ?: 0
    override fun getIncludedCount(evaluation: GuessEvaluation?) = 0
}