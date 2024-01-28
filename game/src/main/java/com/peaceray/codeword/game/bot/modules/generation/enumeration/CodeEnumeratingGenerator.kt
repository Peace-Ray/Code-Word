package com.peaceray.codeword.game.bot.modules.generation.enumeration

import com.peaceray.codeword.game.bot.modules.generation.MonotonicCachingGenerationModule
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import kotlin.math.min
import kotlin.random.Random

/**
 * A class that creates codes by enumerating all possible combinations of the characters in
 * an alphabet. Use for codes formatted like (e.g.) "AAAA", "AAAB", "ACAB", etc. where simply
 * checking the character set and length is sufficient to determine validity.
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
 * @param maxOccurrences The maximum number of a occurrences a given character may have in a valid code
 * @param shuffle Iterate code characters in a randomized order (vs. alphabetical)
 * @param truncateAtProduct Truncate guess enumeration before the *product* of solution list size and
 * guess list size reaches this value.
 * @param seed A random seed used for [shuffle]
 */
class CodeEnumeratingGenerator(
    alphabet: Iterable<Char>,
    val length: Int,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    val maxOccurrences: Int = length,
    val shuffle: Boolean = false,
    val truncateAtLength: Int = 0,
    val truncateAtProduct: Int = 0,
    val seed: Long? = null
): MonotonicCachingGenerationModule(seed ?: Random.nextLong()) {
    private val positionAlphabet: List<List<Char>>
    init {
        val letters = alphabet.distinct().map { it }.toList().sorted()
        val positionAlphabetMut = mutableListOf<List<Char>>()
        for (i in 1..length) {
            positionAlphabetMut.add(if (shuffle) letters.shuffled(random) else letters)
        }
        positionAlphabet = positionAlphabetMut.toList()
    }

    private var solutionsTruncated = false
    private var guessesTruncated = false

    override fun onCacheMissGeneration(constraints: List<Constraint>): Candidates {
        val solutionsPair = generateSolutions(null, true, constraints, constraints)
        val solutions = solutionsPair.first.take(solutionsPair.second).toList()
        solutionsTruncated = solutionsPair.second == solutions.size

        val guessPair = generateGuesses(solutions, null, true, constraints, constraints)
        val guesses = guessPair.first.take(guessPair.second).toList()
        guessesTruncated = guessPair.second == guesses.size

        return Candidates(guesses, solutions)
    }

    override fun onCacheMissFilter(
        candidates: Candidates,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Candidates {
        val solutionsPair = generateSolutions(candidates.solutions, solutionsTruncated, constraints, freshConstraints)
        val solutions = solutionsPair.first.take(solutionsPair.second).toList()
        solutionsTruncated = solutionsPair.second == solutions.size

        val guessPair = generateGuesses(solutions, candidates.guesses, guessesTruncated, constraints, freshConstraints)
        val guesses = guessPair.first.take(guessPair.second).toList()
        guessesTruncated = guessPair.second == guesses.size

        return Candidates(guesses, solutions)
    }

    private fun generateSolutions(
        solutions: Collection<String>?,
        solutionsTruncated: Boolean,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Pair<Sequence<String>, Int> {
        val genSequence: Sequence<String>
        val genConstraints: List<Constraint>

        if (solutions != null && !solutionsTruncated) {
            genSequence = solutions.asSequence()
            genConstraints = freshConstraints
        } else {
            genSequence = codeSequence(constraints, solutionPolicy)
            genConstraints = constraints
        }

        val lengthLimit = if (truncateAtLength <= 0) Int.MAX_VALUE else truncateAtLength
        val solutionList = genSequence
            .filter { code -> genConstraints.all { it.allows(code, solutionPolicy) } }
            .filter { code -> genConstraints.all { it.candidate != code || it.correct } }


        return Pair(solutionList, lengthLimit)
    }

    private fun generateGuesses(
        solutions: Collection<String>,
        guesses: Collection<String>?,
        guessesTruncated: Boolean,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Pair<Sequence<String>, Int> {
        val lengthLimit = if (truncateAtLength <= 0) Int.MAX_VALUE else truncateAtLength
        val productLimit = if (solutions.isEmpty() || truncateAtProduct == 0) {
            Int.MAX_VALUE
        } else {
            truncateAtProduct / solutions.size
        }
        val guessLimit = min(lengthLimit, productLimit)

        val guessSequence = if (guesses != null && !guessesTruncated) {
            guesses.asSequence()
                .filter { code -> freshConstraints.all { it.allows(code, guessPolicy) } }
                .filter { code -> freshConstraints.all { it.candidate != code } }
        } else {
            // prefer guess variety and efficacy: if guessPolicy.isSupersetOf(solutionPolicy), all valid
            // solutions are valid guesses. If the guessLimit is < solution.size then
            // iterating the guess sequence will necessarily select a more homogenous set of
            // guesses than simply sampling within the solution space.
            sequence {
                val solutionGuesses =
                    if (!guessPolicy.isSupersetOf(solutionPolicy)) emptyList() else {
                        solutions.shuffled(random).subList(0, min(guessLimit, solutions.size))
                    }

                yieldAll(solutionGuesses)
                yieldAll(
                    codeSequence(constraints, guessPolicy)
                        .filter { code -> code !in solutionGuesses }
                        .filter { code -> constraints.all { it.allows(code, guessPolicy) } }
                        .filter { code -> constraints.all { it.candidate != code } }
                )
            }
        }

        return Pair(guessSequence, guessLimit)
    }

    private fun codeSequence(
        constraints: List<Constraint> = emptyList(),
        constraintPolicy: ConstraintPolicy = ConstraintPolicy.IGNORE
    ): Sequence<String> {
        var subsequence = positionAlphabet[0].asSequence().map { "$it" }
        for (i in 1 until length) {
            subsequence = subsequence
                .filter { subCode -> constraints.all { it.allows(subCode, constraintPolicy, true) } }
                .flatMap { subCode ->
                    positionAlphabet[(subCode.last().code + subCode.length) % positionAlphabet.size]
                        .filter { char -> subCode.count { it == char } < maxOccurrences }
                        .map { "$subCode$it" }
                        .asSequence()
                }
        }
        return subsequence
    }
}