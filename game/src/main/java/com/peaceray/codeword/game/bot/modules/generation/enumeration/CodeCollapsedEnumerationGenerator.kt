package com.peaceray.codeword.game.bot.modules.generation.enumeration

import com.peaceray.codeword.game.bot.modules.generation.MonotonicCachingGenerationModule
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import kotlin.random.Random

/**
 * A class that creates codes by enumerating a collapsed set of combinations of the characters in
 * an alphabet. Use for codes formatted like (e.g.) "AAAA", "AAAB", "ACAB", etc. where simply
 * checking the character set and length is sufficient to determine validity.
 *
 * Unlike [CodeEnumeratingGenerator], which fully enumerates all code strings for guesses and
 * solutions, this generator uses a "collapsed" enumeration for early guesses. In essence, it
 * uses the fact that (as a first guess) the strings "AAAA" and "AAAB" are distinct, i.e. they
 * provide different information, but the strings "AAAA" and "BBBB" are equivalent. Likewise,
 * "AABC" and "DDEF" are equivalent in terms of information gain. This equivalency falls away in
 * later rounds, as constraints are applied to specific letters, and so the space of candidate
 * guesses expands accordingly.
 *
 * Candidate guesses are constructed letter-by-letter, where the alphabet available at each position
 * is the set of letters previously used plus one more (if any more exist). The letters "previously
 * used" include both those earlier in the same candidate, and those from existing constraints.
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
 * @param shuffle Whether the "letter order" should be randomized
 */
class CodeCollapsedEnumerationGenerator(
    alphabet: Iterable<Char>,
    val length: Int,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    val shuffle: Boolean = false,
    val truncateAtProduct: Int = 0,
    val seed: Long? = null
): MonotonicCachingGenerationModule(seed ?: Random.nextLong()) {
    val alphabet: List<Char> = if (shuffle) {
        alphabet.distinct().toList().shuffled(random)
    } else {
        alphabet.distinct().toList().sorted()
    }

    val size = Math.pow(this.alphabet.size.toDouble(), length.toDouble()).toInt()

    override fun onCacheMissGeneration(constraints: List<Constraint>): Candidates {
        val solutions = codeSequence()
            .filter { code -> constraints.all { it.allows(code, solutionPolicy) } }
            .filter { code -> constraints.all { it.candidate != code || it.correct } }
            .toList()

        val characterHistory = constraints.flatMap { it.candidate.asIterable() }.distinct()
        val guessSource = if (truncateAtProduct == 0 || solutions.isEmpty()) {
            collapsedCodeSequence(characterHistory)
        } else {
            collapsedCodeSequence(characterHistory)
                .take(truncateAtProduct / solutions.size)
        }

        val guesses = guessSource
            .filter { code -> constraints.all { it.allows(code, guessPolicy) } }
            .filter { code -> constraints.all { it.candidate != code } }

        return Candidates(guesses.toList(), solutions)
    }

    override fun onCacheMissFilter(
        candidates: Candidates,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Candidates {
        val solutions = candidates.solutions.asSequence()
            .filter { code -> freshConstraints.all { it.allows(code, solutionPolicy) } }
            .filter { code -> freshConstraints.all { it.candidate != code || it.correct } }
            .toList()

        // TODO attempt to filter the cached guesses. Requires that BOTH of the following are true:
        // 1. The previous guesses used full enumeration; i.e. the full alphabet was used to generate them
        // 2. The previous guesses were NOT truncated.
        val characterHistory = constraints.flatMap { it.candidate.asIterable() }.distinct()
        val guessSource = if (truncateAtProduct == 0 || solutions.isEmpty()) {
            collapsedCodeSequence(characterHistory)
        } else {
            collapsedCodeSequence(characterHistory)
                .take(truncateAtProduct / solutions.size)
        }

        val guesses = guessSource
            .filter { code -> constraints.all { it.allows(code, guessPolicy) } }
            .filter { code -> constraints.all { it.candidate != code } }

        return Candidates(guesses.toList(), solutions)
    }

    private fun collapsedCodeSequence(characterHistory: Iterable<Char>, prefix: String = ""): Sequence<String> {
        if (prefix.length >= length) {
            return sequenceOf(prefix.substring(0, length))
        }

        val (usedAlphabet, unusedAlphabet) = alphabet.partition { it in characterHistory }
        return if (unusedAlphabet.size <= 1) {
            // all possible codes available (from this point)
            codeSequence(prefix)
        } else {
            val nextChars = listOf(usedAlphabet, unusedAlphabet.subList(0, 1)).flatten()
            val nextCharsOrdered = if (shuffle) nextChars.shuffled(random) else nextChars
            nextCharsOrdered.asSequence()
                .flatMap {
                    val nextPrefix = "$prefix$it"
                    val nextHistory = if (it in characterHistory) characterHistory else nextChars
                    collapsedCodeSequence(nextHistory, nextPrefix)
                }
        }
    }

    private fun codeSequence(prefix: String = ""): Sequence<String> {
        if (prefix.length >= length) {
            return sequenceOf(prefix.substring(0, length))
        }

        val width = length - prefix.length
        val size = Math.pow(this.alphabet.size.toDouble(), width.toDouble()).toInt()
        return generateSequence(0) { if (it < size - 1) it + 1 else null }
            .map {
                var num = it
                var code = ""
                for (i in (1..width)) {
                    code = "${alphabet[num % alphabet.size]}$code"
                    num /= alphabet.size
                }
                "$prefix$code"
            }
    }
}