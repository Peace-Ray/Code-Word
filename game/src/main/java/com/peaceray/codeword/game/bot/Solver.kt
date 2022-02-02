package com.peaceray.codeword.game.bot

import com.peaceray.codeword.game.data.Constraint

/**
 * Plays the Code Breaker game by generating guesses based on previously established constraints.
 */
interface Solver {
    /**
     * Given the provided code constraints, generates a guess.
     */
    fun generateGuess(constraints: List<Constraint>): String
}