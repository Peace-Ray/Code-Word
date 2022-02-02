package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateSelectionModule] that simply picks the guess with the Minimum score. In cases where
 * guesses are tied, prefer first a guess that is not a possible solution, and second the guess
 * that appears first in the candidates list. Optionally, set [solutions] = true to always select
 * a possible solution.
 */
class MinimumScoreSelector(val solutions: Boolean = false): CandidateSelectionModule {
    override fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String {
        if (solutions) {
            return candidates.solutions.minByOrNull { scores[it] ?: 0.0 }!!
        }

        val minimumScore = candidates.guesses.minOf { scores[it] ?: 0.0 }
        val tiedGuesses = candidates.guesses.filter { (scores[it] ?: 0.0) == minimumScore }
        return tiedGuesses.firstOrNull { it in candidates.solutions } ?: tiedGuesses.first()
    }
}