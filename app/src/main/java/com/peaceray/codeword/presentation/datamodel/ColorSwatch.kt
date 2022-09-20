package com.peaceray.codeword.presentation.datamodel

import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import com.peaceray.codeword.game.data.Constraint

/**
 * A swatch of colors useful for drawing letters, backgrounds, word evaluations, etc.
 * Includes an application theme that fits the swatch colors, but changes to the ColorSwatch
 * may not necessarily require changes to the application theme (and the theme necessarily
 * does not include all color information useful in drawing game UI elements).
 */
data class ColorSwatch (
    @StyleRes val theme: Int,
    val container: Container,
    val information: InformationSet,
    val evaluation: Evaluation,
    val emoji: EmojiSet,
    val code: Code
) {

    data class Container (
        @ColorInt val background: Int,
        @ColorInt val onBackground: Int,
        @ColorInt val onBackgroundAccent: Int,

        @ColorInt val primary: Int,
        @ColorInt val primaryVariant: Int,
        @ColorInt val onPrimary: Int
    )

    data class Information (
        @ColorInt val info: Int,
        @ColorInt val tip: Int,
        @ColorInt val warn: Int,
        @ColorInt val error: Int,
        @ColorInt val fatal: Int
    ) {
        constructor(vararg c: Int): this(
            info = c.elementAtOrElse(0) { c.last() },
            tip = c.elementAtOrElse(1) { c.last() },
            warn = c.elementAtOrElse(2) { c.last() },
            error = c.elementAtOrElse(3) { c.last() },
            fatal = c.elementAtOrElse(4) { c.last() }
        )

        val color = info

        @ColorInt fun color(warnings: Iterable<com.peaceray.codeword.presentation.datamodel.Information>?): Int =
            color(warnings?.reduceOrNull { acc, warning -> if (acc.level >= warning.level) acc else warning })

        @ColorInt fun color(information: com.peaceray.codeword.presentation.datamodel.Information?): Int = color(information?.level)

        @ColorInt fun color(level: com.peaceray.codeword.presentation.datamodel.Information.Level?): Int = when (level) {
            com.peaceray.codeword.presentation.datamodel.Information.Level.INFO, null -> info
            com.peaceray.codeword.presentation.datamodel.Information.Level.TIP -> tip
            com.peaceray.codeword.presentation.datamodel.Information.Level.WARN -> warn
            com.peaceray.codeword.presentation.datamodel.Information.Level.ERROR -> error
            com.peaceray.codeword.presentation.datamodel.Information.Level.FATAL -> fatal
        }
    }

    data class InformationSet (
        val onBackground: Information,
        val onPrimary: Information
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

    data class Emoji (
        val untried: String,
        val exact: String,
        val included: String,
        val no: String
    ) {
        fun emoji(markup: Constraint.MarkupType?) = when(markup) {
            Constraint.MarkupType.EXACT -> exact
            Constraint.MarkupType.INCLUDED -> included
            Constraint.MarkupType.NO -> no
            else -> untried
        }
    }

    data class EmojiSet (
        val aggregated: Emoji,
        val positioned: Emoji
    )

    data class Code(val colors: List<Int>, val onColors: List<Int>) {
        @ColorInt
        fun color(index: Int) = colors[Math.floorMod(index, colors.size)]
        @ColorInt
        fun onColor(index: Int) = onColors[Math.floorMod(index, onColors.size)]
    }
}