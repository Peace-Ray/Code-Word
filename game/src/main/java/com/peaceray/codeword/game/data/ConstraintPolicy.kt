package com.peaceray.codeword.game.data

/**
 * Policy regarding how previously revealed constraints are applied to new candidates entered by the
 * guesser.
 */
enum class ConstraintPolicy {
    /**
     * Ignore all previous constraints; any valid string may be used.
     */
    IGNORE,

    /**
     * Require that aggregated constraint totals match the new candidates; i.e. the total number
     * of [Constraint.exact] and [Constraint.included] letters must be consistent. This necessarily
     * implies that all remaining characters are different.
     */
    AGGREGATED,

    /**
     * Require all *positive* constraint keys, and only those to match new candidates; i.e.
     * letters marked [Constraint.MarkupType.EXACT] must occur in those locations, and
     * [Constraint.MarkupType.INCLUDED] letters must occur somewhere in the word.
     */
    POSITIVE,

    /**
     * All [POSITIVE] restrictions; additionally, letters marked [Constraint.MarkupType.NO]
     * must not occur in the candidate.
     */
    ALL
}