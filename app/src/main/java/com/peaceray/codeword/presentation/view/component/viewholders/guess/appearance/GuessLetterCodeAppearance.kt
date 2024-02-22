package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import androidx.annotation.Dimension
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout

class GuessLetterCodeAppearance(context: Context, private val layout: GuessLetterCellLayout): GuessLetterAppearance {
    @Dimension private val cellAccentStrokeWidth: Float
    private var codeCharacters: List<Char> = listOf()

    init {
        cellAccentStrokeWidth = context.resources.getDimension(R.dimen.elevated_cell_stroke_width)
    }

    constructor(context: Context, cellLayout: GuessLetterCellLayout, codeCharacters: List<Char>): this(context, cellLayout) {
        this.codeCharacters = codeCharacters
    }

    override fun getColorBg(guess: GuessLetter, swatch: ColorSwatch): Int {
        val charIndex = codeCharacters.indexOf(guess.character)
        return if (guess.isPlaceholder) {
            swatch.evaluation.untried
        } else if (guess.markup == GuessMarkup.NO) {
            swatch.evaluation.color(GuessMarkup.NO)
        } else {
            swatch.code.color(charIndex)
        }
    }

    override fun getColorBgAccent(guess: GuessLetter, swatch: ColorSwatch): Int {
        return if (guess.isPlaceholder) {
            swatch.evaluation.untried
        } else {
            swatch.container.onBackgroundAccent
        }
    }

    override fun getColorText(guess: GuessLetter, swatch: ColorSwatch): Int {
        val charIndex = codeCharacters.indexOf(guess.character)
        return if (guess.isPlaceholder) {
            swatch.evaluation.untried
        } else if (guess.markup == GuessMarkup.NO) {
            swatch.evaluation.onColor(GuessMarkup.NO)
        } else {
            swatch.code.onColor(charIndex)
        }
    }

    override fun getAlphaText(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.0f
        guess.isGuess -> 1.0f
        else -> 1.0f
    }

    override fun getCardElevation(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.0f
        guess.isGuess -> layout.elevation * 1.5f
        else -> layout.elevation
    }

    override fun getCardStrokeWidth(guess: GuessLetter) = when {
        guess.isPlaceholder -> layout.strokeWidth
        else -> cellAccentStrokeWidth
    }

    override fun getCardCornerRadius(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.0f
        guess.isGuess -> layout.cornerRadius
        else -> layout.cornerRadius
    }

    override fun getCardScale(guess: GuessLetter) = when {
        guess.isPlaceholder -> 0.9f
        guess.isGuess -> 1.05f
        else -> 1.0f
    }
}