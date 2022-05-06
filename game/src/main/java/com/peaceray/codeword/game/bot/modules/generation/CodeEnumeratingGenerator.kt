package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
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
 * @param truncateAtProduct Truncate guess enumeration before the *product* of solution list size and
 * guess list size reaches this value.
 */
class CodeEnumeratingGenerator(
    alphabet: Iterable<Char>,
    val length: Int,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    val shuffle: Boolean = false,
    val truncateAtProduct: Int = 0,
    val seed: Long? = null
): MonotonicCachingGenerationModule(seed ?: Random.nextLong()) {
    val alphabet: List<Char> = if (shuffle) {
        alphabet.distinct().toList().shuffled()
    } else {
        alphabet.distinct().toList().sorted()
    }
    val size = Math.pow(this.alphabet.size.toDouble(), length.toDouble()).toInt()

    override fun onCacheMissGeneration(constraints: List<Constraint>): Candidates {
        val solutions = codeSequence()
            .filter { code -> constraints.all { it.candidate != code } }
            .filter { code -> constraints.all { it.allows(code, solutionPolicy) } }
            .toList()

        val guessSource = if (truncateAtProduct == 0 || solutions.isEmpty()) {
            codeSequence()
        } else {
            codeSequence().take(truncateAtProduct / solutions.size)
        }

        val guesses = guessSource
            .filter { code -> constraints.all { it.candidate != code } }
            .filter { code -> constraints.all { it.allows(code, guessPolicy) } }

        return Candidates(guesses.toList(), solutions)
    }

    override fun onCacheMissFilter(
        candidates: Candidates,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Candidates {
        val solutions = candidates.solutions.asSequence()
            .filter { code -> freshConstraints.all { it.candidate != code } }
            .filter { code -> freshConstraints.all { it.allows(code, solutionPolicy) } }
            .toList()

        // TODO attempt to filter the cached guesses; requires determining if they
        // were truncated, and only re-using if not
        val guessSource = if (truncateAtProduct == 0 || solutions.isEmpty()) {
            codeSequence()
        } else {
            codeSequence().take(truncateAtProduct / solutions.size)
        }

        val guesses = guessSource
            .filter { code -> freshConstraints.all { it.candidate != code } }
            .filter { code -> freshConstraints.all { it.allows(code, guessPolicy) } }

        return Candidates(guesses.toList(), solutions)
    }

    private fun codeSequence(): Sequence<String> {
        val characters = if (shuffle) alphabet.shuffled(random) else alphabet
        return generateSequence(0) { if (it < size - 1) it + 1 else null }
            .map {
                var num = it
                var code = ""
                for (i in (1..length)) {
                    code = "${characters[num % characters.size]}$code"
                    num /= characters.size
                }
                code
            }
    }
}