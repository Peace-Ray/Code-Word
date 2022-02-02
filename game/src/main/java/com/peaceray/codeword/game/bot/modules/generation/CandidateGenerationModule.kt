package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A Solver module that generates [Candidates] under specified [Constraint]s.
 * [Candidates] represent the set of possible guesses and possible solutions; in most cases
 * the latter will be a subset of the former.
 */
interface CandidateGenerationModule {
    /**
     * Output a list of Candidates based on current [constraints], which may be empty.
     */
    fun generateCandidates(constraints: List<Constraint> = listOf()): Candidates
}