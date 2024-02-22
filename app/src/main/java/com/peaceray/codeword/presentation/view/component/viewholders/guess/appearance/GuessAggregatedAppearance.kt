package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup

interface GuessAggregatedAppearance {
    // colors and alpha
    @ColorInt fun getColorFill(evaluation: GuessEvaluation?, markup: GuessMarkup, swatch: ColorSwatch): Int
    @ColorInt fun getColorStroke(evaluation: GuessEvaluation?, markup: GuessMarkup, swatch: ColorSwatch): Int

    // dimensions
    val hasStableDimensions: Boolean
    @Dimension fun getPipSize(evaluation: GuessEvaluation?, markup: GuessMarkup): Float
    @Dimension fun getPipMargin(evaluation: GuessEvaluation?, markup: GuessMarkup): Float

    // details
    @Dimension fun getPipStrokeWidth(evaluation: GuessEvaluation?, markup: GuessMarkup): Int
    @Dimension fun getPipCornerRadius(evaluation: GuessEvaluation?, markup: GuessMarkup): Float
    @Dimension fun getPipElevation(evaluation: GuessEvaluation?, markup: GuessMarkup): Float
}