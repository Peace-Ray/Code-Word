package com.peaceray.codeword.game.bot

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.bot.modules.scoring.CandidateScoringModule
import com.peaceray.codeword.game.bot.modules.selection.CandidateSelectionModule

/**
 * An Evaluator that generates secrets in a modular way: generating candidates, scoring them,
 * and selecting one. This generation process is repeated with every [reset], and only then;
 * it plays the game "honestly" (not changing the secret while the game is in progress).
 */
class ModularHonestEvaluator(
    private val generator: CandidateGenerationModule,
    private val scorer: CandidateScoringModule,
    private val selector: CandidateSelectionModule
): Evaluator {
    private var code: String = ""
        get() {
            synchronized(this) {
                if (field == "") {
                    field = generateCode()
                }
                return field
            }
        }
    override fun evaluate(candidate: String, constraints: List<Constraint>) = Constraint.create(candidate, code)

    override fun peek(constraints: List<Constraint>) = code

    override fun reset() {
        synchronized(this) {
            code = generateCode()
        }
    }

    private fun generateCode(): String {
        val candidates = generator.generateCandidates()
        val scores = scorer.scoreCandidates(candidates)
        return selector.selectCandidate(candidates, scores)
    }
}