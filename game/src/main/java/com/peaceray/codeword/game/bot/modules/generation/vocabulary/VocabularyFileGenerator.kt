package com.peaceray.codeword.game.bot.modules.generation.vocabulary

import com.peaceray.codeword.game.bot.modules.generation.MonotonicCachingGenerationModule
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.validators.Validator
import com.peaceray.codeword.game.validators.Validators
import java.io.File
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
class VocabularyFileGenerator(
    filenames: List<String>,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    guessFilenames: List<String>? = null,
    solutionFilenames: List<String>? = null,
    filter: Validator = Validators.pass(),
    seed: Long? = null
): MonotonicCachingGenerationModule(seed ?: Random.nextLong()) {

    constructor(
        filename: String,
        guessPolicy: ConstraintPolicy,
        solutionPolicy: ConstraintPolicy,
        guessFilename: String? = null,
        solutionFilename: String? = null,
        filter: Validator = Validators.pass(),
        seed: Long? = null
    ) : this(
        listOf(filename),
        guessPolicy,
        solutionPolicy,
        if (guessFilename == null) null else listOf(guessFilename),
        if (solutionFilename == null) null else listOf(solutionFilename),
        filter,
        seed
    ) {

    }

    private val guessFilenames = guessFilenames ?: filenames
    private val solutionFilenames = solutionFilenames ?: filenames

    private val guessVocabulary: List<String> by lazy {
        val wordList = mutableListOf<String>()
        this.guessFilenames.forEach { wordList.addAll(File(it).readLines()) }
        wordList.filter { it.isNotBlank() }
            .filter(filter)
            .distinct()
    }

    private val solutionVocabulary: List<String> by lazy {
        if (this.guessFilenames == this.solutionFilenames) {
            this.guessVocabulary
        } else {
            val wordList = mutableListOf<String>()
            this.solutionFilenames.forEach { wordList.addAll(File(it).readLines()) }
            wordList.filter { it.isNotBlank() }
                .filter(filter)
                .distinct()
        }
    }

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