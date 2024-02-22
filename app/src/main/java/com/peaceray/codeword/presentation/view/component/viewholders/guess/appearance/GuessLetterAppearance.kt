package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import androidx.annotation.ColorInt
import androidx.annotation.Dimension
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter

interface GuessLetterAppearance {
    // colors and alpha
    @ColorInt fun getColorBg(guess: GuessLetter, swatch: ColorSwatch): Int
    @ColorInt fun getColorBgAccent(guess: GuessLetter, swatch: ColorSwatch): Int
    @ColorInt fun getColorText(guess: GuessLetter, swatch: ColorSwatch): Int
    fun getAlphaText(guess: GuessLetter): Float

    // dimensions
    @Dimension fun getCardElevation(guess: GuessLetter): Float
    @Dimension fun getCardStrokeWidth(guess: GuessLetter): Float
    @Dimension fun getCardCornerRadius(guess: GuessLetter): Float
    @Dimension fun getCardScale(guess: GuessLetter): Float

    // pip
}