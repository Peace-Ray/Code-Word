package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy

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
     * Given the provided code constraints, generate feedback. If a callback function is provided,
     * it will be invoked at least once, upon completion. FeedbackProvider implementations are
     * free to repeatedly invoke the callback as Feedback is updated, setting `done` false for
     * each except the final invocation.
     *
     * @param policy The ConstraintPolicy under which Constraints should be considered.
     * @param constraints The Constraints to examine.
     * @param callback Optionally, a callback for partially constructed Feedbacks (Feedback
     * instances which are correct, but not complete, with more granular analysis still pending).
     * Input parameters: feedback (the analysis) and done (whether the analysis provided is
     * complete). Return: whether the analysis was accepted; if so, no more calls will be made.
     */
    fun getFeedback(
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)? = null
    ): Feedback

    /**
     * Is this FeedbackProvider capable of generating Feedback under this policy?
     */
    fun supports(policy: ConstraintPolicy): Boolean

}