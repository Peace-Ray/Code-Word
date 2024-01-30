package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess

interface GuessAggregatedAppearance {
    // colors and alpha
    @ColorInt fun getColorFill(guess: Guess, markup: Constraint.MarkupType?, swatch: ColorSwatch): Int
    @ColorInt fun getColorStroke(guess: Guess, markup: Constraint.MarkupType?, swatch: ColorSwatch): Int

    // dimensions
    val hasStableDimensions: Boolean
    @Dimension fun getPipSize(guess: Guess, markup: Constraint.MarkupType?): Float
    @Dimension fun getPipMargin(guess: Guess, markup: Constraint.MarkupType?): Float

    // details
    @Dimension fun getPipStrokeWidth(guess: Guess, markup: Constraint.MarkupType?): Int
    @Dimension fun getPipCornerRadius(guess: Guess, markup: Constraint.MarkupType?): Float
    @Dimension fun getPipElevation(guess: Guess, markup: Constraint.MarkupType?): Float


    // pip / text counts
    fun getTotalCount(guess: Guess): Int
    fun getExactCount(guess: Guess): Int
    fun getIncludedCount(guess: Guess): Int
}