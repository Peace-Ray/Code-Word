package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import java.io.File

/**
 * A [CandidateGenerationModule] based on an explicit vocabulary list, e.g. English words of
 * a certain length. New codes are not generated; the initially provide list is filtered by
 * [Constraint]s.
 *
 * Appropriate for "find the word" games, which use a [solutionPolicy] of
 * [ConstraintPolicy.ALL] (guessers are presented with explicit markup on each letter) and a
 * [guessPolicy] of either [ConstraintPolicy.IGNORE] or [ConstraintPolicy.POSITIVE] depending on
 * whether "hard mode" is active.
 */
class VocabularyFileGenerator(
    filenames: List<String>,
    val guessPolicy: ConstraintPolicy,
    val solutionPolicy: ConstraintPolicy,
    guessFilenames: List<String>? = null,
    solutionFilenames: List<String>? = null
): MonotonicCachingGenerationModule() {

    constructor(
        filename: String,
        guessPolicy: ConstraintPolicy,
        solutionPolicy: ConstraintPolicy,
        guessFilename: String? = null,
        solutionFilename: String? = null
    ) : this(
        listOf(filename),
        guessPolicy,
        solutionPolicy,
        if (guessFilename == null) null else listOf(guessFilename),
        if (solutionFilename == null) null else listOf(solutionFilename)
    ) {

    }

    val guessFilenames = guessFilenames ?: filenames
    val solutionFilenames = solutionFilenames ?: filenames

    val guessVocabulary: List<String> by lazy {
        val wordList = mutableListOf<String>()
        this.guessFilenames.forEach { wordList.addAll(File(it).readLines()) }
        wordList.filter { it.isNotBlank() }
            .distinct()
    }

    val solutionVocabulary: List<String> by lazy {
        if (this.guessFilenames == this.solutionFilenames) {
            this.guessVocabulary
        } else {
            val wordList = mutableListOf<String>()
            this.solutionFilenames.forEach { wordList.addAll(File(it).readLines()) }
            wordList.filter { it.isNotBlank() }
                .distinct()
        }
    }

    override fun onCacheMissGeneration(constraints: List<Constraint>): Candidates {
        val guesses = guessVocabulary.asSequence()
            .filter { code -> constraints.all { it.candidate != code } }
            .filter { code -> constraints.all { it.allows(code, guessPolicy) } }

        val solutions = solutionVocabulary.asSequence()
            .filter { code -> constraints.all { it.candidate != code } }
            .filter { code -> constraints.all { it.allows(code, solutionPolicy) } }

        return Candidates(guesses.toList(), solutions.toList())
    }

    override fun onCacheMissFilter(
        candidates: Candidates,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Candidates {
        val guesses = candidates.guesses.asSequence()
            .filter { code -> freshConstraints.all { it.candidate != code } }
            .filter { code -> freshConstraints.all { it.allows(code, guessPolicy) } }

        val solutions = candidates.solutions.asSequence()
            .filter { code -> freshConstraints.all { it.candidate != code } }
            .filter { code -> freshConstraints.all { it.allows(code, solutionPolicy) } }

        return Candidates(guesses.toList(), solutions.toList())
    }
}