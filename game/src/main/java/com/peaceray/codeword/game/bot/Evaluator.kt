package com.peaceray.codeword.game.bot

import com.peaceray.codeword.game.data.Constraint

/**
 * Plays the Code Breaker game as the "secret keeper" by evaluating guesses according to some
 * internal secret, with the promise that at any time a valid code can be revealed that matches
 * all evaluated constraints.
 */
interface Evaluator {
    /**
     */
    fun evaluate(candidate: String, constraints: List<Constraint>): Constraint
    /**
     * Reveal the secret code.
     */
    fun peek(constraints: List<Constraint>): String

    /**
     * Clears all state caches and guess progress, resetting the Evaluator to a "just initialized"
     * state. Useful e.g. for Evaluators that select a secret in advance and hold it in all cases
     * (a new secret will be picked).
     */
    fun reset()
}