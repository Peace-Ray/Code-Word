package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint

/**
 * A data class summarizing the known information regarding a potential character of the solution.
 */
data class CharacterFeedback (
    val character: Char,
    val occurrences: IntRange,
    val positions: Set<Int> = emptySet(),
    val markup: Constraint.MarkupType? = when {
        positions.isNotEmpty() -> Constraint.MarkupType.EXACT
        occurrences.last == 0 -> Constraint.MarkupType.NO
        occurrences.first > 0 -> Constraint.MarkupType.INCLUDED
        else -> null
    }
)