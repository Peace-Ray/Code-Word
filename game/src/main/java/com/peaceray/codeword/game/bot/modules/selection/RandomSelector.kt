package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateSelectionModule] that simply picks the guess with the Maximum score. In cases where
 * guesses are tied, prefer first a guess that is also a possible solution, and second the guess
 * that appears first in the candidates list.
 */
class RandomSelector(val solutions: Boolean = false): CandidateSelectionModule {
    override fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String {
        val codes = if (solutions) candidates.solutions else candidates.guesses
        return codes.random()
    }
}