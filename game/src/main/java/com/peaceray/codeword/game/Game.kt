package com.peaceray.codeword.game

import com.peaceray.codeword.game.data.*
import com.peaceray.codeword.game.data.Constraint

/**
 * A Game represents an ongoing (or completed) interaction between two players: the secret
 * keeper and the guesser. The guesser plays first by presenting a candidate secret; the keeper
 * responds by evaluating that guess (counting and marking exact and misplaced -- "value" -- chars).
 * The game is over when the guesser runs out of rounds (and loses), or successfully guesses
 * the exact code (and wins). Because the role of keeper can be played by a trivial string comparison
 * function we generally refer to the guesser as the active player; e.g. a game is "Won" if the guesser
 * has succeeded.
 *
 * A valid guess must have the expected letter count, and pass arbitrary validation (e.g. must
 * use only an approved set of characters, must be in a vocabulary list, etc.). In {settings.hard}
 * mode, each guess must match all previous key evaluations. (For Bulls and Cows rules, do not
 * use "hard" mode, since the guesser is not presented with positional information, just the number
 * of exact and value matches).
 *
 * The Game class does not actively solicit player actions. It passively accepts player
 * input, only checking whether the action is appropriate given the state of the game. Apply your
 * own control structure as appropriate to your use case.
 */
class Game(val settings: Settings, val validator: (String) -> Boolean) {
    enum class State { GUESSING, EVALUATING, WON, LOST }
    enum class GuessError { LENGTH, VALIDATION, CONSTRAINTS }
    enum class EvaluationError { GUESS }

    class IllegalGuessException(val error: GuessError, message: String): IllegalArgumentException(message)
    class IllegalEvaluationException(val error: EvaluationError, message: String): IllegalArgumentException(message)

    /**
     * The most recent guess provided by the guesser and not yet evaluated.
     * Will be null if all guesses have evaluations, including if there are no guesses.
     */
    var currentGuess: String? = null
        private set

    /**
     * The evaluated guesses.
     */
    private val _constraints: MutableList<Constraint> = mutableListOf()
    val constraints: List<Constraint>
        get() = _constraints.toList()

    val state: State
        get() {
            if (constraints.any { it.correct }) {
                return State.WON
            } else if (constraints.size == settings.rounds) {
                return State.LOST
            } else if (currentGuess != null) {
                return State.EVALUATING
            } else {
                return State.GUESSING
            }
        }

    val won: Boolean
        get() = state == State.WON

    val lost: Boolean
        get() = state == State.LOST

    val over: Boolean
        get() = won || lost

    val round: Int
        get() { return if (over) constraints.size else constraints.size + 1 }

    @Throws(IllegalStateException::class, IllegalGuessException::class)
    fun guess(candidate: String) {
        if (state != State.GUESSING) {
            throw IllegalStateException("Can't set a guess in state $state")
        }

        if (candidate.length != settings.letters) {
            throw IllegalGuessException(
                GuessError.LENGTH,
                "Guess had ${candidate.length} letters; must have ${settings.letters}"
            )
        }

        if (!validator(candidate)) {
            throw IllegalGuessException(
                GuessError.VALIDATION,
                "Guess failed validation"
            )
        }

        if (!constraints.all { it.allows(candidate, settings.constraintPolicy) }) {
            throw IllegalGuessException(
                GuessError.CONSTRAINTS,
                "Guess does not match previous evaluation constraints"
            )
        }

        currentGuess = candidate
    }

    @Throws(IllegalStateException::class, IllegalEvaluationException::class)
    fun evaluate(constraint: Constraint) {
        if (state != State.EVALUATING) {
            throw IllegalStateException("Can't set a guess evaluation in state $state")
        }

        if (constraint.candidate != currentGuess) {
            throw IllegalEvaluationException(
                EvaluationError.GUESS,
                "Evaluation did not match current guess"
            )
        }

        currentGuess = null
        _constraints.add(constraint)
    }

    fun reset() {
        currentGuess = null
        _constraints.clear()
    }
}