package com.peaceray.codeword.presentation.manager.feedback.guess

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.datamodel.guess.GuessAlphabet
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup.Companion.toGuessMarkup
import com.peaceray.codeword.presentation.datamodel.guess.GuessType
import javax.inject.Inject

/**
 * Creates Guesses without any additional hint or feedback information; purely delivers the exact
 * information provided by the Constraint.
 *
 * For by-letter evaluations ([ConstraintPolicy]s in {Positive, All, Perfect} includes markup
 * on a per-letter basis for evaluations, but not guesses. Otherwise, does not include per-letter
 * markup and instead presents an evaluation consistent with the policy.
 *
 * This GuessCreator produces original, pre-hint behavior, with the input Feedback objects
 * completely ignored.
 */
class VanillaGuessCreator @Inject constructor(
    private val constraintPolicy: ConstraintPolicy
): GuessCreator {

    override fun toGuess(partialGuess: String, feedback: Feedback) =
        Guess.createGuess(feedback.candidates.size, partialGuess)

    override fun toGuess(constraint: Constraint, feedback: Feedback): Guess {
        val evaluation = when (constraintPolicy) {
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

        val letters = when (constraintPolicy) {
            // aggregated constraint evaluation: no markup on letters
            ConstraintPolicy.IGNORE,
            ConstraintPolicy.AGGREGATED_EXACT,
            ConstraintPolicy.AGGREGATED_INCLUDED,
            ConstraintPolicy.AGGREGATED -> List(constraint.candidate.length) {
                GuessLetter(it, constraint.candidate[it], type = GuessType.EVALUATION)
            }
            // by-letter evaluation: apply markup to letters
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> List(constraint.candidate.length) {
                GuessLetter(it, constraint.candidate[it], constraint.markup[it].toGuessMarkup())
            }
        }

        return Guess.createFromLetters(constraint.candidate.length, letters, evaluation)
    }

    override fun toGuessAlphabet(feedback: Feedback): GuessAlphabet {
        return when (constraintPolicy) {
            ConstraintPolicy.IGNORE -> GuessAlphabet(feedback.characters.values.associate { cf ->
                Pair(cf.character, GuessAlphabet.Letter(
                    cf.character,
                    cf.occurrences
                ))
            })
            ConstraintPolicy.AGGREGATED_EXACT,
            ConstraintPolicy.AGGREGATED_INCLUDED,
            ConstraintPolicy.AGGREGATED -> {
                GuessAlphabet(feedback.characters.values.associate { cf ->
                    Pair(cf.character, GuessAlphabet.Letter(cf, markup = GuessMarkup.EMPTY))
                })
            }
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> {
                GuessAlphabet(feedback.characters.values.associate { cf ->
                    Pair(cf.character, GuessAlphabet.Letter(cf))
                })
            }
        }
    }
}