package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import androidx.annotation.Dimension
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GuessLetter
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout

class GuessLetterEntryAppearance(context: Context, private val layout: GuessLetterCellLayout): GuessLetterAppearance {
    @Dimension
    private val cellAccentStrokeWidth: Float

    init {
        cellAccentStrokeWidth = context.resources.getDimension(R.dimen.elevated_cell_stroke_width)
    }

    override fun getColorBg(guess: GuessLetter, swatch: ColorSwatch) = swatch.evaluation.color(null)

    override fun getColorBgAccent(guess: GuessLetter, swatch: ColorSwatch) = if (guess.isPlaceholder) swatch.evaluation.untried else swatch.container.onBackgroundAccent

    override fun getColorText(guess: GuessLetter, swatch: ColorSwatch) = swatch.evaluation.onColor(null)

    override fun getAlphaText(guess: GuessLetter) = if (guess.isPlaceholder) 0f else 1.0f

    override fun getCardElevation(guess: GuessLetter) = if (guess.isPlaceholder) 0f else layout.elevation

    override fun getCardStrokeWidth(guess: GuessLetter) = if (guess.isPlaceholder) layout.strokeWidth else cellAccentStrokeWidth

    override fun getCardCornerRadius(guess: GuessLetter) = if (guess.isPlaceholder) 0f else layout.cornerRadius

    override fun getCardScale(guess: GuessLetter) = if (guess.isPlaceholder) 0.9f else 1.0f
}