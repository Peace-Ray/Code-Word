package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * Calculate an arbitrary "score" for an arbitrary guess conditioned on any possible ground truth
 * solution. Can be considered instead as a score of each underlying solution, relative to the
 * unevaluated guess.
 */
interface CandidateInvertedScoringModule {
    fun scoreSolutions(guess: String, candidates: Candidates): Map<String, Double>
}