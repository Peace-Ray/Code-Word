package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import kotlin.math.log

/**
 * A [CandidateScoringModule] calculating the "Information Gain" IG(X, Y) of each candidate guess.
 * In information theory, IG(Y, X) is the difference in the entropy of Y and the entropy of Y
 * given X: in this case, the entropy of the underlying secret code given existing constraints, and
 * its entropy once the candidate guess receives evaluation. The latter is calculated as
 * the weighted average of the entropy of all possible outcomes (evaluations).
 *
 * Unlike the Knuth minimum, which avoids "worst-case" guesses, this scorer is more concerned with
 * "expected-case" performance. It also supports priors on solutions in the form of an
 * optional [weight] function -- by default all solutions are equally likely, having weight(x) -> 1.
 */
class InformationGainScorer(val eliminationPolicy: ConstraintPolicy, val weight: (String) -> Double = { 1.0 }): CandidateScoringModule {
    override fun scoreCandidates(candidates: Candidates): Map<String, Double> {
        val (currentEntropy, totalWeight) = candidates.solutions.wordEntropyAndWeight()
        return candidates.guesses.associateWith { guess ->
            // break into word sets for each possible evaluation and map to weight value
            val resultSets = candidates.solutions
                .groupBy({ Constraint.asKey(guess, it, eliminationPolicy) }, weight)
                .values

            // weighted average of conditional entropy among result sets
            resultSets.fold(0.0) { acc, weights ->
                val (setEntropy, setWeight) = weights.entropyAndWeight()
                acc + setEntropy * (setWeight / totalWeight)
            }
        }.mapValues { currentEntropy - it.value }
    }

    private fun Collection<String>.wordEntropyAndWeight(): Pair<Double, Double> {
        return map(weight).entropyAndWeight()
    }

    private fun Collection<Double>.entropyAndWeight(): Pair<Double, Double> {
        val totalWeight = sum()
        val entropy = fold(0.0) { acc, w ->
            val proportion = w / totalWeight
            val e = -proportion * log(proportion, 2.0)
            acc + e
        }
        return Pair(entropy, totalWeight)
    }
}