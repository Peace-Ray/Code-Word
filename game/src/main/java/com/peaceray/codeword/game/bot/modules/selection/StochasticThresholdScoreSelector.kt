package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateSelectionModule] that determines the maximum available score and picks, at random,
 * among those candidates whose scores are within a certain threshold margin of it. Solutions are
 * preferred to non-solution candidates by an optional bias adjustment.
 *
 * @param threshold In [0, 1]; a candidate guess 'g' in G may be selected iff
 * score'(g) >= threshold * max_G(score')
 * @param solutionBias In [0, 1]; the degree to which solution candidates are preferred over
 * non-solutions. score'(g) = (g in S ? 1 : 1 - solutionBias) * score(g)
 */
class StochasticThresholdScoreSelector(val threshold: Double = 0.9, val solutionBias: Double = 0.1): CandidateSelectionModule {
    override fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String {
        if (candidates.solutions.size == 1) {
            return candidates.solutions.first()
        }

        val biasedScores = scores.mapValues {
            if (it.key in candidates.solutions) {
                it.value
            } else {
                it.value * (1 - solutionBias)
            }
        }

        val thresholdScore = threshold * biasedScores.values.maxOrNull()!!

        return candidates.guesses
            .filter { (biasedScores[it] ?: 0.0) >= thresholdScore }
            .random()
    }
}