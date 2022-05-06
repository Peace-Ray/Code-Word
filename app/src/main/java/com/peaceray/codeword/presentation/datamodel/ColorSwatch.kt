package com.peaceray.codeword.presentation.datamodel

import androidx.annotation.ColorInt
import com.peaceray.codeword.game.data.Constraint

/**
 * A swatch of colors useful for drawing letters, backgrounds, word evaluations, etc.
 */
data class ColorSwatch (
    val container: Container,
    val evaluation: Evaluation,
    val code: Code
) {

    data class Container (
        @ColorInt val background: Int,
        @ColorInt val onBackground: Int,

        @ColorInt val primary: Int,
        @ColorInt val primaryVariant: Int,
        @ColorInt val onPrimary: Int
    )

    data class Evaluation (
        @ColorInt val untried: Int,
        @ColorInt val untriedVariant: Int,
        @ColorInt val onUntried: Int,

        @ColorInt val exact: Int,
        @ColorInt val exactVariant: Int,
        @ColorInt val onExact: Int,

        @ColorInt val included: Int,
        @ColorInt val includedVariant: Int,
        @ColorInt val onIncluded: Int,

        @ColorInt val no: Int,
        @ColorInt val noVariant: Int,
        @ColorInt val onNo: Int
    ) {
        @ColorInt fun color(markup: Constraint.MarkupType?) = when(markup) {
            Constraint.MarkupType.EXACT -> exact
            Constraint.MarkupType.INCLUDED -> included
            Constraint.MarkupType.NO -> no
            else -> untried
        }

        @ColorInt fun colorVariant(markup: Constraint.MarkupType?) = when(markup) {
            Constraint.MarkupType.EXACT -> exactVariant
            Constraint.MarkupType.INCLUDED -> includedVariant
            Constraint.MarkupType.NO -> noVariant
            else -> untriedVariant
        }

        @ColorInt fun onColor(markup: Constraint.MarkupType?) = when(markup) {
            Constraint.MarkupType.EXACT -> onExact
            Constraint.MarkupType.INCLUDED -> onIncluded
            Constraint.MarkupType.NO -> onNo
            else -> onUntried
        }
    }

    data class Code(val colors: List<Int>, val onColors: List<Int>) {
        @ColorInt fun color(index: Int) = colors[index % colors.size]
        @ColorInt fun onColor(index: Int) = onColors[index % onColors.size]
    }
}