package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateSelectionModule] that simply picks the guess with the Maximum score. In cases where
 * guesses are tied, prefer first a guess that is also a possible solution, and second the guess
 * that appears first in the candidates list. Optionally, set [solutions] = true to always select
 * a possible solution.
 */
class MaximumScoreSelector(val solutions: Boolean = false): CandidateSelectionModule {
    override fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String {
        if (candidates.solutions.size == 1) {
            return candidates.solutions.first()
        }

        if (solutions) {
            return candidates.solutions.maxByOrNull { scores[it] ?: 0.0 }!!
        }

        val maximumScore = scores.values.maxOrNull()!!
        val tiedGuesses = candidates.guesses.filter { scores[it] == maximumScore }
        return tiedGuesses.firstOrNull { it in candidates.solutions } ?: tiedGuesses.first()
    }
}