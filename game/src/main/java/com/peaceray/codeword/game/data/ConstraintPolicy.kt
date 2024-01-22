package com.peaceray.codeword.game.data

/**
 * Policy regarding how previously revealed constraints are applied to new candidates entered by the
 * guesser.
 */
enum class ConstraintPolicy {
    /**
     * Ignore all previous constraints; any valid string may be used.
     */
    IGNORE {
        override fun isSupersetOf(policy: ConstraintPolicy) = true
        override fun isSubsetOf(policy: ConstraintPolicy) = policy == IGNORE
    },

    /**
     * Require that the [Constraint.exact] aggregated totals match the new candidates; i.e. the total
     * number of [Constraint.exact] letters must be consistent.
     *
     * Is a superset of [AGGREGATED], as [Constraint.included] is ignored entirely.
     */
    AGGREGATED_EXACT {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy in setOf(AGGREGATED_EXACT, AGGREGATED, ALL, PERFECT)
        override fun isSubsetOf(policy: ConstraintPolicy) = policy in setOf(IGNORE, AGGREGATED_EXACT)
    },

    /**
     * Require that the sum of [Constraint.exact] and [Constraint.included] aggregated totals
     * match the new candidates; i.e. the total number of letters that occur in the word
     * (whether in their exact positions or not) must be consistent.
     *
     * Is a superset of [AGGREGATED], as the position of [Constraint.exact] matches is ignored.
     */
    AGGREGATED_INCLUDED {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy in setOf(AGGREGATED_INCLUDED, AGGREGATED, ALL, PERFECT)
        override fun isSubsetOf(policy: ConstraintPolicy) = policy in setOf(IGNORE, AGGREGATED_INCLUDED)
    },

    /**
     * Require that aggregated constraint totals match the new candidates; i.e. the total number
     * of [Constraint.exact] and [Constraint.included] letters must be consistent. This necessarily
     * implies that all remaining characters are different.
     *
     * Is a distinct, overlapping set to [POSITIVE].
     */
    AGGREGATED {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy in setOf(AGGREGATED, ALL, PERFECT)
        override fun isSubsetOf(policy: ConstraintPolicy) = policy in setOf(IGNORE, AGGREGATED_EXACT, AGGREGATED_INCLUDED, AGGREGATED)
    },

    /**
     * Require all *positive* constraint keys, and only those to match new candidates; i.e.
     * letters marked [Constraint.MarkupType.EXACT] must occur in those locations, and
     * [Constraint.MarkupType.INCLUDED] letters must occur somewhere in the word.
     *
     * Is a distinct, overlapping set to [AGGREGATED].
     */
    POSITIVE {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy in setOf(POSITIVE, ALL, PERFECT)
        override fun isSubsetOf(policy: ConstraintPolicy) = policy in setOf(IGNORE, POSITIVE)
    },

    /**
     * All [POSITIVE] restrictions; additionally, letters marked [Constraint.MarkupType.NO]
     * must not occur in the candidate.
     */
    ALL {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy in setOf(ALL, PERFECT)
        override fun isSubsetOf(policy: ConstraintPolicy) = policy != PERFECT
    },

    /**
     * Perfectly restricted. Any letters marked [Constraint.MarkupType.EXACT] must occur in those
     * locations. Any letters marked [Constraint.MarkupType.INCLUDED] must occur, but in different
     * locations (this is the main difference from [ALL]). Any letters marked [Constraint.MarkupType.NO]
     * must not occur.
     *
     * Note that it is possible for a letter to appear more than once, with different markup; they
     * are dealt with in the order listed above.
     */
    PERFECT {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy == PERFECT
        override fun isSubsetOf(policy: ConstraintPolicy) = true
    };

    /**
     * Returns whether this [ConstraintPolicy], and the way it determines the allowance / prohibition
     * of candidate guesses, represents a *superset* of [policy]. It other words, whether every
     * guess that would be permitted by [policy] is also permitted by the receiver.
     */
    abstract fun isSupersetOf(policy: ConstraintPolicy): Boolean

    /**
     * Returns whether this [ConstraintPolicy], and the way it determines the allowance / prohibition
     * of candidate guesses, represents a *subset* of [policy]. It other words, whether every
     * guess that would be permitted by the receiver is also permitted by [policy].
     */
    abstract fun isSubsetOf(policy: ConstraintPolicy): Boolean
}