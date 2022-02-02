package com.peaceray.codeword.game.bot.modules.selection

import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * Selects one word among candidates based on their assessed scores.
 */
interface CandidateSelectionModule {
    fun selectCandidate(candidates: Candidates, scores: Map<String, Double>): String
}