package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint

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