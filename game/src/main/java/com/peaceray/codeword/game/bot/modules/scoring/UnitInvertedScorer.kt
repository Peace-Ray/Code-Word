package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A Scoring module that provide a score for 1 for all candidate guesses.
 */
class UnitInvertedScorer: CandidateInvertedScoringModule {
    override fun scoreSolutions(guess: String, candidates: Candidates): Map<String, Double> {
        return candidates.solutions.associateWith { 1.0 }
    }
}