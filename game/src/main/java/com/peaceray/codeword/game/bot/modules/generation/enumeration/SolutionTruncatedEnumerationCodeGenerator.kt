package com.peaceray.codeword.game.bot.modules.generation.enumeration

import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.bot.modules.shared.Seeded
import kotlin.random.Random

/**
 * A class that creates codes by enumerating combinations of the characters in
 * an alphabet. Use for codes formatted like (e.g.) "AAAA", "AAAB", "ACAB", etc. where simply
 * checking the character set and length is sufficient to determine validity.
 *
 * Unlike most generators, does not enumerate guesses separately from solutions and does not
 * monotonically shrink the solution set as constraints are added. Instead, generates potential
 * solutions up to some size limit, then uses that list as the available guesses. This incomplete
 * list of solutions is useful in contexts where complete solution enumeration would be unacceptably
 * expensive.
 *
 * The use case is for a dishonest evaluator, willing to change the solution based on player guesses
 * until only one possible solution remains.
 *
 * Appropriate for games like Master Mind or Code Breaker, with each character representing a
 * different color of solution peg. Those games use a [solutionPolicy] of [ConstraintPolicy.AGGREGATED],
 * where the guesser is only presented with the total number of exact and included letters (not
 * their positions), and a [guessPolicy] of [ConstraintPolicy.IGNORE], where any guess was accepted,
 * including those known to be wrong based on previous evaluations.
 *
 * @param alphabet The code character set
 * @param length The code length
 * @param guessPolicy How Constraints are applied to guess lists
 * @param solutionPolicy How Constraints are applied to solution lists
 * @param truncateAtProduct Truncate guess enumeration before the *product* of solution list size and
 * guess list size reaches this value.
 */
class SolutionTruncatedEnumerationCodeGenerator(
    alphabet: Iterable<Char>,
    val length: Int,
    val solutionPolicy: ConstraintPolicy,
    val shuffle: Boolean = false,
    val truncateAtSize: Int = 0,
    val pretruncateAtSize: Int = 0,
    val seed: Long? = null
): CandidateGenerationModule, Seeded(seed ?: Random.nextLong()) {
    val positionAlphabet: List<List<String>>
    init {
        val letters = alphabet.distinct().map { "$it" }.toList().sorted()
        val positionAlphabetMut = mutableListOf<List<String>>()
        for (i in 1..length) {
            positionAlphabetMut.add(if (shuffle) letters.shuffled(random) else letters)
        }
        positionAlphabet = positionAlphabetMut.toList()
    }

    override fun generateCandidates(constraints: List<Constraint>): Candidates {
        var codeSequence: Sequence<String>? = null
        for (i in 1..length) {
            codeSequence = extendCodes(constraints, codeSequence)
        }

        val codes = codeSequence ?: sequenceOf()
        val codeList = (if (truncateAtSize > 0) codes.take(truncateAtSize) else codes).toList()
        return Candidates(codeList, codeList)
    }

    /**
     * Given a list of current constraints and partial codes, extend the list of codes by one
     * additional character.
     *
     * @param constraints The active constraints on the solution.
     * @param codes Partial codes, comprised of the code alphabet and with constant length <= [length]
     * @return A pair: extended codes, and whether that code list can be extended again.
     */
    private fun extendCodes(constraints: List<Constraint>, codes: Sequence<String>?): Sequence<String> {
        // get a sequence of code candidates by extending codes by one character, eliminate all
        // such codes that don't fit the constraints, then truncate the list.
        val extended = when {
            codes == null -> positionAlphabet[0].asSequence()     // kickstart from one character
            codes.first().length == length -> codes               // no extension (finished)
            else -> codes.flatMap { partialCode ->
                val alphabet = positionAlphabet[(partialCode.last().toInt() + partialCode.length) % positionAlphabet.size]
                alphabet.map { "$partialCode$it" }
            }
        }.filter { code ->
            constraints.all { constraint ->
                constraint.allows(code, solutionPolicy, partial = true)
            }
        }

        return if (pretruncateAtSize  > 0) extended.take(pretruncateAtSize) else extended
    }
}