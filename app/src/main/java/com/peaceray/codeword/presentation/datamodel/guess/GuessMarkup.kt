package com.peaceray.codeword.presentation.datamodel.guess

import com.peaceray.codeword.game.data.Constraint

/**
 * A logical extension of Constraint.MarkupType, which adds "A", a value indicating that the
 * letter has been explicitly marked to indicate that it's probably fine.
 */
enum class GuessMarkup {
    /**
     * This letter occurs in this exact position.
      */
    EXACT,

    /**
     * The letter occurs in the correct code, but not in this position.
     */
    INCLUDED,

    /**
     * The letter is allowed here. It may or may not be in this position.
     */
    ALLOWED,

    /**
     * The letter does not occur in the correct code.
     */
    NO,

    /**
     * No markup; no information. Equivalent to 'null' but may be used in non-nullable fields.
     */
    EMPTY;

    companion object {
        fun Constraint.MarkupType?.toGuessMarkup() = when (this) {
            Constraint.MarkupType.EXACT -> EXACT
            Constraint.MarkupType.INCLUDED -> INCLUDED
            Constraint.MarkupType.NO -> NO
            null -> EMPTY
        }

        val explicit = setOf(EXACT, INCLUDED, NO)
        val informative = setOf(EXACT, INCLUDED, ALLOWED, NO)
    }
}