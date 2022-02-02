package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateScoringModule] applying Knuth's MiniMax calculation (the "Mini" part): for each
 * candidate guess,  take the "score" as the minimum number of solutions that might be eliminated
 * across all possible evaluations ([Constraint]s) that could be applied by the secret keeper.
 */
class KnuthMinimumScorer(val eliminationPolicy: ConstraintPolicy): CandidateScoringModule {
    override fun scoreCandidates(candidates: Candidates): Map<String, Double> {
        return candidates.guesses.associateWith { guess ->
            candidates.solutions.groupBy { Constraint.asKey(guess, it, eliminationPolicy) }
                .minOf { candidates.solutions.size - it.value.size }
                .toDouble()
        }
    }
}