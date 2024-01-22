package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint

/**
 * A class which analyzes constraints, potentially in light of pre-existing information about
 * the code secret (e.g. the alphabet, that it contains no duplicate letters, etc.), and produces
 * Feedback and CharacterFeedback. Those data objects can potentially power an automatic solver
 * or be communicated to the user to guide their next guesses.
 *
 * There is no guarantee of completeness in the feedback provided; it may be that a more careful
 * analysis could further constrain the possibility space. However, it is assumed that all feedback
 * is correct, in the sense that the actual solution must pass the validator(s) provided by them.
 * Feedback thus establishes bounds on the solution space, with different FeedbackProviders shaping
 * that space differently but all such bounds containing the solution itself.
 *
 * Note that a perfect FeedbackProvider essentially removes any need for the player to make
 * any deductions themselves, so more limited Providers may be preferred.
 */
interface FeedbackProvider {

    /**
     * Given the provided code constraints, generate feedback.
     */
    fun getFeedback(policy: ConstraintFeedbackPolicy, constraints: List<Constraint>): Feedback

    /**
     * Given the provided code constraints, generate character feedback.
     */
    fun getCharacterFeedback(policy: ConstraintFeedbackPolicy, constraints: List<Constraint>): Map<Char, CharacterFeedback>

    /**
     * Is this FeedbackProvider capable of generating Feedback under this policy?
     */
    fun supports(policy: ConstraintFeedbackPolicy): Boolean

}