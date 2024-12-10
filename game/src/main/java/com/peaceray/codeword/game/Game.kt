package com.peaceray.codeword.game

import com.peaceray.codeword.game.data.*
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.validators.Validator
import java.util.*

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
 *
 * Serialization: Although Game is not a DataClass (because the arbitrary [validator] function
 * makes otherwise-identical Games behave differently) its internal state, represented
 * as game moves, can still be serialized as [constraints] and [currentGuess].
 * Deserialization and restoration requires reproducing the original [validator] function.
 * Use [Game.atMove] to restore the game to the previous state (require repeating move
 * logic).
 */
class Game(settings: Settings, val validator: Validator, uuid: UUID? = null) {

    /**
     * Creates a game and quickly plays through the specified moves, returning the
     * result only when it has reached the indicated state. Provides a way to
     * load a persisted Game. Note that Game is not a Data Class; the validator function
     * prevents its direct serialization.
     */
    companion object {
        @Throws(
            IllegalStateException::class,
            IllegalGuessException::class,
            IllegalEvaluationException::class
        )
        fun atMove(
            settings: Settings,
            validator: Validator,
            uuid: UUID,
            constraints: List<Constraint>,
            currentGuess: String? = null
        ): Game {
            val game = Game(settings, validator, uuid)
            constraints.forEach {
                game.guess(it.candidate)
                game.evaluate(it)
            }
            if (currentGuess != null) {
                game.guess(currentGuess)
            }
            return game
        }
    }

    enum class State(val isOver: Boolean) {
        GUESSING(false),
        EVALUATING(false),
        WON(true),
        LOST(true);
    }

    enum class SettingsError { LETTERS, ROUNDS, CONSTRAINT_POLICY }
    enum class GuessError { LENGTH, VALIDATION, CONSTRAINTS }
    enum class EvaluationError { GUESS }

    class IllegalSettingsException(val error: SettingsError, message: String): IllegalArgumentException(message)
    class IllegalGuessException(val error: GuessError, val violations: List<Constraint.Violation>?, message: String): IllegalArgumentException("$message: $violations")
    class IllegalEvaluationException(val error: EvaluationError, message: String): IllegalArgumentException(message)

    val uuid = uuid ?: UUID.randomUUID()

    var settings: Settings = settings
        private set

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
        get() = when {
            constraints.any { it.correct } -> State.WON
            constraints.size == settings.rounds -> State.LOST
            currentGuess != null -> State.EVALUATING
            else -> State.GUESSING
        }

    val started: Boolean
        get() = over || currentGuess != null || round > 1

    val won: Boolean
        get() = state == State.WON

    val lost: Boolean
        get() = state == State.LOST

    val over: Boolean
        get() = state.isOver

    val round: Int
        get() { return if (over) constraints.size else constraints.size + 1 }

    fun canUpdateSettings(settings: Settings) = !over
            && (this.settings.letters == settings.letters || !started)
            && (this.settings.rounds >= round)
            && (this.settings.constraintPolicy.isSubsetOf(settings.constraintPolicy) || !started)

    @Throws(IllegalStateException::class, IllegalSettingsException::class)
    fun updateSettings(settings: Settings) {
        when {
            over -> throw IllegalStateException("Can't update settings in state $state")
            this.settings.letters != settings.letters && started ->
                throw IllegalSettingsException(SettingsError.LETTERS, "Can't change number of letters once the Game is started")
            this.settings.rounds < round ->
                throw IllegalSettingsException(SettingsError.ROUNDS, "Can't change number of rounds to less than already played")
            !this.settings.constraintPolicy.isSubsetOf(settings.constraintPolicy) && started ->
                throw IllegalSettingsException(SettingsError.CONSTRAINT_POLICY, "Can't update to a more restrictive ConstraintPolicy once the game is started")
        }

        this.settings = settings
    }

    @Throws(IllegalStateException::class, IllegalGuessException::class)
    fun guess(candidate: String) {
        if (state != State.GUESSING) {
            throw IllegalStateException("Can't set a guess in state $state")
        }

        if (candidate.length != settings.letters) {
            throw IllegalGuessException(
                GuessError.LENGTH,
                null,
                "Guess had ${candidate.length} letters; must have ${settings.letters}"
            )
        }

        if (!validator(candidate)) {
            throw IllegalGuessException(
                GuessError.VALIDATION,
                null,
                "Guess failed validation"
            )
        }

        val violations = constraints.asSequence()
            .map { it.violations(candidate, settings.constraintPolicy) }
            .firstOrNull { it.isNotEmpty() }
        if (violations != null) {
            throw IllegalGuessException(
                GuessError.CONSTRAINTS,
                violations,
                "Guess does not match previous evaluation constraints under ${settings.constraintPolicy} policy"
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