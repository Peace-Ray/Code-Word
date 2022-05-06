package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.bot.modules.shared.Seeded
import kotlin.random.Random

/**
 * A [CandidateSelectionModule] that simply picks the guess with the Maximum score. In cases where
 * guesses are tied, prefer first a guess that is also a possible solution, and second the guess
 * that appears first in the candidates list.
 */
class RandomSelector(val solutions: Boolean = false, val seed: Long? = null):
    CandidateSelectionModule,
    Seeded(seed ?: Random.nextLong())
{
    override fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String {
        val codes = if (solutions) candidates.solutions else candidates.guesses
        return codes.random(random)
    }
}