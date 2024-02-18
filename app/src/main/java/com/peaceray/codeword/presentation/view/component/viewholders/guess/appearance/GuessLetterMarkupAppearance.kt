package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import androidx.annotation.Dimension
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout

class GuessLetterMarkupAppearance(context: Context, private val layout: GuessLetterCellLayout): GuessLetterAppearance {
    @Dimension
    private val cellAccentStrokeWidth: Float

    init {
        cellAccentStrokeWidth = context.resources.getDimension(R.dimen.elevated_cell_stroke_width)
    }

    override fun getColorBg(guess: GuessLetter, swatch: ColorSwatch) = swatch.evaluation.color(guess.markup)

    override fun getColorBgAccent(guess: GuessLetter, swatch: ColorSwatch) = if (guess.isPlaceholder) swatch.evaluation.untried else swatch.container.onBackgroundAccent

    override fun getColorText(guess: GuessLetter, swatch: ColorSwatch) = swatch.evaluation.onColor(guess.markup)

    override fun getAlphaText(guess: GuessLetter) = if (guess.isPlaceholder) 0f else 1.0f

    override fun getCardElevation(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.0f
        guess.isGuess -> layout.elevation * 1.5f
        else -> layout.elevation
    }

    override fun getCardStrokeWidth(guess: GuessLetter) = if (guess.isPlaceholder) layout.strokeWidth else cellAccentStrokeWidth

    override fun getCardCornerRadius(guess: GuessLetter) = if (guess.isPlaceholder) 0f else layout.cornerRadius

    override fun getCardScale(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.9f
        guess.isGuess -> 1.05f
        else -> 1.0f
    }
}