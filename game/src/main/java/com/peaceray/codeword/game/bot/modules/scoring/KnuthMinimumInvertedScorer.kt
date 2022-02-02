package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateScoringModule] applying Knuth's MiniMax calculation (the "Mini" part): for each
 * candidate solution, take the "score" as the number of solutions that would be eliminated by
 * the resulting [Constraint] created from the guess. A correct guess naturally eliminates
 * all solutions and has the highest possible score.
 */
class KnuthMinimumInvertedScorer(val eliminationPolicy: ConstraintPolicy): CandidateInvertedScoringModule {
    override fun scoreSolutions(guess: String, candidates: Candidates): Map<String, Double> {
        val constraintSolutions = candidates.solutions.groupBy { Constraint.asKey(guess, it, eliminationPolicy) }
        val constraintScores = constraintSolutions.mapValues { candidates.solutions.size.toDouble() - it.value.size }

        val scores = mutableMapOf<String, Double>()
        constraintSolutions.forEach { (constraintKey, solutions) ->
            val score = constraintScores[constraintKey]!!
            solutions.forEach { scores[it] = score }
        }

        if (guess in candidates.solutions) {
            scores[guess] = candidates.solutions.size.toDouble()
        }

        return scores
    }
}