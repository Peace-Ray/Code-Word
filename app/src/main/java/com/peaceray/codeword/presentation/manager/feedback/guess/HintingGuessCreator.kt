package com.peaceray.codeword.presentation.manager.feedback.guess

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.datamodel.guess.GuessType
import javax.inject.Inject

/**
 * Creates Guesses that include hints based on calculated [Feedback]. Types of hints included:
 *
 * 1. Markup applied to evaluation Guesses for letters whose markup is fully known. Markup is applied
 * only in cases where its meaning is unambiguous. For instance, in a AGGREGATED_INCLUDED game, there
 * is never any indication that a letter is in the right spot, so "Included" does not necessarily
 * convey "not in this place."
 *
 * 2. Specify the candidates that could potentially occupy the given position, evaluated from
 * left to right. This means (e.g.) that if a letter has at most 1 occurrence, and it has been
 * used at an earlier position in the word, it will NOT be included in the candidates for that
 * spot (even if the Feedback object includes it there).
 */
class HintingGuessCreator @Inject constructor(
    private val constraintPolicy: ConstraintPolicy
): GuessCreator {

    override fun toGuess(partialGuess: String, feedback: Feedback): Guess {
        // candidates for each letter, displayed as hints along the way
        val candidates = getProgressiveCandidates(partialGuess, feedback)
        // the Guess itself (no Markup is displayed for guesses).
        return Guess.createFromPaddedLetters(
            candidates.size,
            candidates.mapIndexed { index, chars ->
                if (index >= partialGuess.length) GuessLetter(index, candidates = chars) else {
                    GuessLetter(index, partialGuess[index], candidates = chars)
                }
            },
            null
        )
    }

    override fun toGuess(constraint: Constraint, feedback: Feedback): Guess {
        val markup = getMarkup(constraint, feedback)
        val evaluation = getEvaluation(constraint, feedback)

        return Guess.createFromLetters(
            constraint.candidate.length,
            constraint.candidate.mapIndexed { index, c ->
                GuessLetter(index, c, markup = markup[index], type = GuessType.EVALUATION)
            },
            evaluation
        )
    }

    private fun getProgressiveCandidates(candidate: String, feedback: Feedback): List<Set<Char>> {
        val letterCount = mutableMapOf<Char, Int>()
        return feedback.candidates.mapIndexed { index, chars ->
            val progressiveChars = chars.filter { char ->
                (letterCount[char] ?: 0) < (feedback.occurrences[char]?.last ?: 0)
            }.toSet()
            if (index < candidate.length) {
                val char = candidate[index]
                letterCount[char] = (letterCount[char] ?: 0) + 1
            }
            progressiveChars
        }
    }

    private fun getMarkup(constraint: Constraint, feedback: Feedback): List<Constraint.MarkupType?> {
        if (constraint.correct) {
            // correct answer: mark everything EXACT
            return List(constraint.candidate.length) { Constraint.MarkupType.EXACT }
        }

        return when (constraintPolicy) {
            ConstraintPolicy.IGNORE -> List(constraint.candidate.length) { null }
            ConstraintPolicy.AGGREGATED_EXACT,
            ConstraintPolicy.AGGREGATED_INCLUDED,
            ConstraintPolicy.AGGREGATED -> {
                // create a basic markup list with certainties
                val markup = MutableList(constraint.candidate.length) { index ->
                    val char = constraint.candidate[index]
                    val range = feedback.occurrences[char] ?: 0..0
                    val candidates = feedback.candidates[index]

                    when {
                        // character not present anywhere in the word
                        range.last == 0 -> Constraint.MarkupType.NO
                        // this position is known; character is correct
                        candidates.size == 1 && char in candidates -> Constraint.MarkupType.EXACT

                        // otherwise, mark as null
                        else -> null
                    }
                }

                // populate with additional markup where possible. for each character,
                // count the occurrences in the candidate word and compare against ranges.
                // mark as INCLUDED any up to the minimum count that are not in that position's candidates.
                // mark as NO any beyond the maximum count that are not in that position's candidates.
                val chars = constraint.candidate.toSet()
                chars.forEach { char ->
                    val positions = constraint.candidate.indices.filter { constraint.candidate[it] == char }
                    val (maybePositions, noPositions) = positions.partition { char in feedback.candidates[it] }
                    val range = feedback.occurrences[char] ?: 0..0
                    var consideredCount = maybePositions.size
                    noPositions.forEach { index ->
                        if (consideredCount < range.first) markup[index] = Constraint.MarkupType.INCLUDED
                        else if (range.last <= consideredCount) markup[index] = Constraint.MarkupType.NO
                        consideredCount++
                    }
                }

                // downgrade to the best set markup set for that character, if less specific
                constraint.candidate.forEachIndexed { index, char ->
                    markup[index] = listOf(markup[index], feedback.characters[char]?.markup)
                        .minByOrNull { it?.value() ?: -1 }
                }

                markup.toList()
            }
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> List(constraint.candidate.length) { constraint.markup[it] }
        }
    }

    private fun getEvaluation(constraint: Constraint, feedback: Feedback) = when (constraintPolicy) {
        ConstraintPolicy.IGNORE -> null
        ConstraintPolicy.AGGREGATED_EXACT ->
            GuessEvaluation(constraint.exact, 0, constraint.candidate.length, constraint.correct)
        ConstraintPolicy.AGGREGATED_INCLUDED ->
            GuessEvaluation(0, constraint.exact + constraint.included, constraint.candidate.length, constraint.correct)
        ConstraintPolicy.AGGREGATED,
        ConstraintPolicy.POSITIVE,
        ConstraintPolicy.ALL,
        ConstraintPolicy.PERFECT -> GuessEvaluation(constraint.exact, constraint.included, constraint.candidate.length, constraint.correct)
    }

    private fun Constraint.MarkupType.value() = when(this) {
        Constraint.MarkupType.EXACT -> 2
        Constraint.MarkupType.INCLUDED -> 1
        Constraint.MarkupType.NO -> 0
    }
}