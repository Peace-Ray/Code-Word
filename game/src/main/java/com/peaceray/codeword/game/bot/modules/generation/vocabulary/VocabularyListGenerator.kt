package com.peaceray.codeword.game.bot.modules.generation.vocabulary

import com.peaceray.codeword.game.bot.modules.generation.MonotonicCachingGenerationModule
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.validators.Validator
import com.peaceray.codeword.game.validators.Validators
import kotlin.random.Random

/**
 * A [CandidateGenerationModule] based on an explicit vocabulary list, e.g. English words of
 * a certain length. New codes are not generated; the initially provide list is filtered by
 * [Constraint]s.
 *
 * Appropriate for "find the word" games, which use a [solutionPolicy] of
 * [ConstraintPolicy.PERFECT] (guessers are presented with explicit markup on each letter) and a
 * [guessPolicy] of either [ConstraintPolicy.IGNORE] or [ConstraintPolicy.POSITIVE] depending on
 * whether "hard mode" is active.
 */
class VocabularyListGenerator(
    vocabulary: List<String>,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    guessVocabulary: List<String>? = null,
    solutionVocabulary: List<String>? = null,
    filter: Validator = Validators.pass(),
    seed: Long? = null
): MonotonicCachingGenerationModule(seed ?: Random.nextLong()) {
    private val guessVocabulary = (guessVocabulary ?: vocabulary).filter(filter)
    private val solutionVocabulary = (solutionVocabulary ?: vocabulary).filter(filter)

    override fun onCacheMissGeneration(constraints: List<Constraint>): Candidates {
        val guesses = guessVocabulary.asSequence()
            .filter { code -> constraints.all { it.allows(code, guessPolicy) } }
            .filter { code -> constraints.all { it.candidate != code } }

        val solutions = solutionVocabulary.asSequence()
            .filter { code -> constraints.all { it.allows(code, solutionPolicy) } }
            .filter { code -> constraints.all { it.candidate != code || it.correct } }

        return Candidates(guesses.toList(), solutions.toList())
    }

    override fun onCacheMissFilter(
        candidates: Candidates,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Candidates {
        val guesses = candidates.guesses.asSequence()
            .filter { code -> freshConstraints.all { it.allows(code, guessPolicy) } }
            .filter { code -> freshConstraints.all { it.candidate != code } }

        val solutions = candidates.solutions.asSequence()
            .filter { code -> freshConstraints.all { it.allows(code, solutionPolicy) } }
            .filter { code -> freshConstraints.all { it.candidate != code || it.correct } }

        return Candidates(guesses.toList(), solutions.toList())
    }
}