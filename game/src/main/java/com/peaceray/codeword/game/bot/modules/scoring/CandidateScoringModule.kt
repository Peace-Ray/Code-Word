package com.peaceray.codeword.game.bot.modules.scoring

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * Calculate an arbitrary "score" for each candidate considered as a potential next guess.
 * Scores may be freely scaled or displaced based on what makes the most sense for your scoring
 * algorithm (or is most appropriate for the selection module), but it is assumed that all scores
 * are non-negative and higher scores indicate better candidates. A score of "0" may or may not
 * represent a zero-information guess.
 */
interface CandidateScoringModule {
    fun scoreCandidates(candidates: Candidates): Map<String, Double>
}