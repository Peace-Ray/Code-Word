package com.peaceray.codeword.game.feedback

/**
 * The type of feedback that is presented to the game's Solver for each Constraint. Note that
 * Constraints include complete per-character markup, but depending on Game settings and UI,
 * only a more limited view of this data might be presented to the user.
 *
 * ConstraintFeedback determines the information directly presented to the user per-Constraint,
 * and also the information usable
 */
enum class ConstraintFeedbackPolicy {

    /**
     * Each character in the guess is given with associated markup: EXACT, INCLUDED, NO.
     */
    CHARACTER_MARKUP,

    /**
     * Per-character markup is aggregated into counts: the number of EXACT, INCLUDED, and NO
     * matches. Note that often only EXACT and INCLUDED counts will be displayed, while NO
     * can be inferred from the length of the guess.
     */
    AGGREGATED_MARKUP,

    /**
     * Per-character markup is aggregated: the number of EXACT and INCLUDED matches are summed
     * and the result presented to the user. NO can be inferred from the length of the guess.
     */
    COUNT_INCLUDED,

    /**
     * Per-character markup is aggregated: the number of EXACT matches are provided. Note that
     * INCLUDED and NO cannot be directly inferred from a single Constraint.
     */
    COUNT_EXACT;

}