package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation

interface GuessAggregatedAppearance {
    // colors and alpha
    @ColorInt fun getColorFill(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?, swatch: ColorSwatch): Int
    @ColorInt fun getColorStroke(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?, swatch: ColorSwatch): Int

    // dimensions
    val hasStableDimensions: Boolean
    @Dimension fun getPipSize(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float
    @Dimension fun getPipMargin(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float

    // details
    @Dimension fun getPipStrokeWidth(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Int
    @Dimension fun getPipCornerRadius(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float
    @Dimension fun getPipElevation(evaluation: GuessEvaluation?, markup: Constraint.MarkupType?): Float


    // pip / text counts
    fun getTotalCount(evaluation: GuessEvaluation?): Int
    fun getExactCount(evaluation: GuessEvaluation?): Int
    fun getIncludedCount(evaluation: GuessEvaluation?): Int
}