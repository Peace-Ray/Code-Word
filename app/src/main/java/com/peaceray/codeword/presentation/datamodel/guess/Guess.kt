package com.peaceray.codeword.presentation.datamodel.guess

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup.Companion.toGuessMarkup

data class Guess private constructor(
    val length: Int,
    val candidate: String,
    val letters: List<GuessLetter>,
    val lettersPadded: List<GuessLetter>,
    val evaluation: GuessEvaluation?
) {

    //region Instance Creation
    //---------------------------------------------------------------------------------------------
    companion object {
        private const val EMPTY = ""

        fun createPlaceholder(length: Int) = Guess(
            length,
            EMPTY,
            emptyList(),
            List(length) { GuessLetter(it) },
            null
        )

        fun createPlaceholder(candidates: List<Set<Char>>) = Guess(
            candidates.size,
            EMPTY,
            emptyList(),
            candidates.mapIndexed { index, chars -> GuessLetter(index, candidates = chars) },
            null
        )

        fun createGuess(length: Int, guess: String) = Guess(
            length,
            guess,
            List(guess.length) { GuessLetter(it, guess[it] ) },
            List(length) { if (it in guess.indices) GuessLetter(it, guess[it]) else GuessLetter(it) },
            null
        )

        fun createGuess(candidates: List<Set<Char>>, guess: String) = Guess(
            candidates.size,
            guess,
            List(guess.length) { GuessLetter(it, guess[it], candidates = candidates[it] ) },
            List(candidates.size) { if (it in guess.indices) GuessLetter(it, guess[it], candidates = candidates[it]) else GuessLetter(it, candidates = candidates[it]) },
            null
        )

        fun createPerfectEvaluation(constraint: Constraint) = Guess(
            constraint.candidate.length,
            constraint.candidate,
            List(constraint.candidate.length) { GuessLetter(it, constraint.candidate[it], constraint.markup[it].toGuessMarkup(), null) },
            List(constraint.candidate.length) { GuessLetter(it, constraint.candidate[it], constraint.markup[it].toGuessMarkup(), null) },
            GuessEvaluation(constraint.exact, constraint.included, constraint.candidate.length, constraint.correct)
        )

        fun createNoMarkupEvaluation(constraint: Constraint) = Guess(
            constraint.candidate.length,
            constraint.candidate,
            List(constraint.candidate.length) { GuessLetter(it, constraint.candidate[it], GuessMarkup.EMPTY, null) },
            List(constraint.candidate.length) { GuessLetter(it, constraint.candidate[it], GuessMarkup.EMPTY, null) },
            GuessEvaluation(constraint.exact, constraint.included, constraint.candidate.length, constraint.correct)
        )

        fun createNoMarkupEvaluation(guess: String, exact: Int, included: Int, correct: Boolean = guess.length == exact) = Guess(
            guess.length,
            guess,
            List(guess.length) { GuessLetter(it, guess[it], GuessMarkup.EMPTY, null) },
            List(guess.length) { GuessLetter(it, guess[it], GuessMarkup.EMPTY, null) },
            GuessEvaluation(exact, included, guess.length, correct)
        )

        fun createFromLetters(length: Int, letters: List<GuessLetter>, evaluation: GuessEvaluation?) = Guess(
            length,
            letters.map { it.character }.joinToString(""),
            letters,
            List(length) { if (it < letters.size) letters[it] else GuessLetter(it) },
            evaluation
        )

        fun createFromPaddedLetters(length: Int, paddedLetters: List<GuessLetter>, evaluation: GuessEvaluation?): Guess {
            val letters = paddedLetters.filter { !it.isPlaceholder }
            val candidate = letters.map { it.character }.joinToString("")

            return Guess(
                length,
                candidate,
                letters,
                paddedLetters,
                evaluation
            )
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Type Accessors
    //---------------------------------------------------------------------------------------------
    val type: GuessType = when {
        evaluation != null -> GuessType.EVALUATION
        candidate.isNotEmpty() -> GuessType.GUESS
        else -> GuessType.PLACEHOLDER
    }

    val isPlaceholder = type == GuessType.PLACEHOLDER
    val isGuess = type == GuessType.GUESS
    val isEmptyGuess = type == GuessType.GUESS && candidate.isEmpty()
    val isEvaluation = type == GuessType.EVALUATION
    //---------------------------------------------------------------------------------------------
    //endregion

}