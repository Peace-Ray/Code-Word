package com.peaceray.codeword.presentation.manager.feedback.guess

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.presentation.datamodel.guess.Guess

/**
 * Creates Guess instances based on internal settings and available Feedback.
 */
interface GuessCreator {

    /**
     * Convert a partially entered guess string to a Guess instance, which may or may not include
     * additional information drawn from Feedback, as appropriate.
     */
    fun toGuess(partialGuess: String, feedback: Feedback): Guess

    /**
     * Wrap an existing Constraint as a Guess, which may or may not include additional information
     * drawn from Feedback, as appropriate.
     */
    fun toGuess(constraint: Constraint, feedback: Feedback): Guess

}