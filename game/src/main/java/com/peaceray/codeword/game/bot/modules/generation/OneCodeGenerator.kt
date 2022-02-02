package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * Generates one candidate, and one candidate only, regardless of constraints.
 */
class OneCodeGenerator(val code: String): CandidateGenerationModule {
    private val candidates = Candidates(listOf(code), listOf(code))

    override fun generateCandidates(constraints: List<Constraint>) = candidates
}