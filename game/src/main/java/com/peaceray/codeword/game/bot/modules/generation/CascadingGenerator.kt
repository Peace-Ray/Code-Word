package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateGenerationModule] built from [CandidateGenerationModule]s, which are applied in order;
 * if one module does not provide at least [minimumCandidates] guesses or solutions, the next
 * module is used. This may produce non-optimal behavior (limiting the available guesses in earlier
 * steps) but can make the first few game rounds much faster to compute by limiting the solution
 * space in a trivial way.
 */
class CascadingGenerator(val generators: List<CandidateGenerationModule>, val guesses: Int = 1, val solutions: Int = 1, val product: Int = 1): CandidateGenerationModule {
    constructor(
        guesses: Int = 1,
        solutions: Int = 1,
        vararg generators: CandidateGenerationModule
    ): this (guesses = guesses, solutions = solutions, generators = generators.toList()) {

    }

    override fun generateCandidates(constraints: List<Constraint>): Candidates {
        return generators.take(generators.size - 1).asSequence()
            .map { it.generateCandidates(constraints) }
            .firstOrNull {
                it.guesses.size >= guesses
                        && it.solutions.size >= solutions
                        && it.guesses.size * it.solutions.size >= product
            }
            ?: generators[generators.size - 1].generateCandidates(constraints)
    }
}