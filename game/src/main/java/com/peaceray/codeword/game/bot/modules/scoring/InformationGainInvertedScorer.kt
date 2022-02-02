package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import kotlin.math.log

/**
 * A [CandidateScoringModule] calculating the "Information Gain" IG(X, Y) of the candidate guess
 * under different realities (different possible ground truths). In cases where the solution itself
 * is guessed, the resulting entropy is 0 and IG is equal to previous entropy.
 *
 * In information theory, IG(Y, X) is the difference in the entropy of Y and the entropy of Y
 * given X: in this case, the entropy of the underlying secret code given existing constraints, and
 * its entropy once the candidate guess receives evaluation. The latter is calculated as
 * the weighted average of the entropy of all possible outcomes (evaluations).
 *
 * Unlike the Knuth minimum, which avoids "worst-case" guesses, this scorer is more concerned with
 * "expected-case" performance. It also supports priors on solutions in the form of an
 * optional [weight] function -- by default all solutions are equally likely, having weight(x) -> 1.
 */
class InformationGainInvertedScorer(val eliminationPolicy: ConstraintPolicy, val weight: (String) -> Double = { 1.0 }): CandidateInvertedScoringModule {
    override fun scoreSolutions(guess: String, candidates: Candidates): Map<String, Double> {
        val currentEntropy = candidates.solutions.entropy()

        // break into word sets for each possible evaluation
        val constraintSolutions = candidates.solutions
            .groupBy { Constraint.asKey(guess, it, eliminationPolicy) }

        // calculate conditional entropy within each set
        val constraintInformationGain = constraintSolutions
            .mapValues { currentEntropy - it.value.entropy() }

        // invert map to score individual solutions
        val scores = mutableMapOf<String, Double>()
        constraintSolutions.forEach { (constraintKey, solutions) ->
            val score = constraintInformationGain[constraintKey]!!
            solutions.forEach { scores[it] = score }
        }

        if (guess in candidates.solutions) {
            scores[guess] = currentEntropy
        }

        return scores
    }

    private fun Collection<String>.entropy(): Double {
        val totalWeight = sumByDouble(weight)
        return map(weight)
            .map {
                val proportion = it / totalWeight
                -proportion * log(proportion, 2.0)
            }
            .sum()
    }
}