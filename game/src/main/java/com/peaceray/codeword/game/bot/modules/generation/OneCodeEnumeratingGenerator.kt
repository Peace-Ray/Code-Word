package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.bot.modules.shared.Seeded
import kotlin.random.Random

/**
 * A class that logically generates codes by enumerating all possible combinations of the characters in
 * an alphabet. Use for codes formatted like (e.g.) "AAAA", "AAAB", "ACAB", etc. where simply
 * checking the character set and length is sufficient to determine validity.
 *
 * Unlike [CodeEnumeratingGenerator], this implementation selects exactly one code of the generated
 * list and provides it as a candidate, in all circumstances, regardless of constraints. The
 * generated code is based on the provided Seed value.
 *
 * Appropriate for games like Master Mind or Code Breaker, with each character representing a
 * different color of solution peg. Those games use a [solutionPolicy] of [ConstraintPolicy.AGGREGATED],
 * where the guesser is only presented with the total number of exact and included letters (not
 * their positions), and a [guessPolicy] of [ConstraintPolicy.IGNORE], where any guess was accepted,
 * including those known to be wrong based on previous evaluations.
 *
 * @param alphabet The code character set
 * @param length The code length
 * @param seed A seed value for random code generation
 */
class OneCodeEnumeratingGenerator(
    alphabet: Iterable<Char>,
    val length: Int,
    val seed: Long? = null
): CandidateGenerationModule, Seeded(seed ?: Random.nextLong()) {

    private val letters = alphabet.distinct().toList().sorted()
    private val code = List(length) { "${letters.random(random)}" }.joinToString(separator = "")
    private val candidates = Candidates(listOf(code), listOf(code))

    override fun generateCandidates(constraints: List<Constraint>) = candidates
}