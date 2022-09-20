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
     * Require that aggregated constraint totals match the new candidates; i.e. the total number
     * of [Constraint.exact] and [Constraint.included] letters must be consistent. This necessarily
     * implies that all remaining characters are different.
     *
     * Is a distinct, overlapping set to [POSITIVE].
     */
    AGGREGATED {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy == AGGREGATED || policy == ALL || policy == PERFECT
        override fun isSubsetOf(policy: ConstraintPolicy) = policy == IGNORE || policy == AGGREGATED
    },

    /**
     * Require all *positive* constraint keys, and only those to match new candidates; i.e.
     * letters marked [Constraint.MarkupType.EXACT] must occur in those locations, and
     * [Constraint.MarkupType.INCLUDED] letters must occur somewhere in the word.
     *
     * Is a distinct, overlapping set to [AGGREGATED].
     */
    POSITIVE {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy == POSITIVE || policy == ALL || policy == PERFECT
        override fun isSubsetOf(policy: ConstraintPolicy) = policy == IGNORE || policy == POSITIVE
    },

    /**
     * All [POSITIVE] restrictions; additionally, letters marked [Constraint.MarkupType.NO]
     * must not occur in the candidate.
     */
    ALL {
        override fun isSupersetOf(policy: ConstraintPolicy) = policy == ALL || policy == PERFECT
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