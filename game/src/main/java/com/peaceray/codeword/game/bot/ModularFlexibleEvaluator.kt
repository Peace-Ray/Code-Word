package com.peaceray.codeword.game.bot

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.bot.modules.scoring.CandidateInvertedScoringModule
import com.peaceray.codeword.game.bot.modules.selection.CandidateSelectionModule

/**
 * An Evaluator that cheats in a modular way. Does not generate and retain a single code;
 * instead, provides flexible guess evaluations based on inverted score calculations. In other words,
 * the evaluation given to each guess is not based on a canonical ground-truth secret but on which
 * evaluation produces the best outcome according to its internal modules.
 *
 * One example is a generous evaluator; one that lets the guesser "win" after a certain number of
 * turns by marking the next [Constraint]-fitting guess correct. Another is a malicious evaluator;
 * one that evaluates guesses so as to provide the least possible amount of information until
 * constrained to one single possible solution.
 *
 * The function [peek] will provide a candidate solution that is consistent with constraints, but
 * may not match the eventual correct answer.
 */
class ModularFlexibleEvaluator(
    private val generator: CandidateGenerationModule,
    private val scorer: CandidateInvertedScoringModule,
    private val selector: CandidateSelectionModule
): Evaluator {
    override fun evaluate(candidate: String, constraints: List<Constraint>): Constraint {
        val candidates = generator.generateCandidates(constraints)
        val scores = scorer.scoreSolutions(candidate, candidates)
        val secret = selector.selectCandidate(candidates, scores)

        return Constraint.create(candidate, secret)
    }

    override fun peek(constraints: List<Constraint>): String {
        return generator.generateCandidates(constraints).solutions.first()
    }

    override fun reset() {
        // nothing to change
    }
}