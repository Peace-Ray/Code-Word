package com.peaceray.codeword.presentation.contracts

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import java.util.*

/**
 * A Contract describing presenter-view interaction to run a Game. The Game seed and setup are
 * provided to the View, which provides them to the Presenter.
 */
interface GameContract: BaseContract {
    enum class ErrorType {
        WORD_EMPTY,
        WORD_LENGTH,
        WORD_NOT_RECOGNIZED,
        WORD_NOT_CONSTRAINED,
        CODE_EMPTY,
        CODE_LENGTH,
        CODE_INVALID,
        CODE_NOT_CONSTRAINED,
        EVALUATION_INCONSISTENT,
        UNKNOWN
    }

    interface View: BaseContract.View {

        //region Pass in Creation Data
        //-----------------------------------------------------------------------------------------

        /**
         * Provide the game seed passed in to the View upon creation / use.
         *
         * @return The seed, if any
         */
        fun getGameSeed(): String?

        /**
         * Provide the GameSetup object passed in to the View upon creation / use.
         *
         * @return A [GameSetup] instance
         */
        fun getGameSetup(): GameSetup

        /**
         * Provide the updated GameSetup object for an in-progress Game whose settings have
         * been changed. If nothing has changed, return the original GameSetup.
         */
        fun getUpdatedGameSetup(): GameSetup

        /**
         * Provide a guess, or partial guess, cached from a previous view display but possibly
         * not saved as part of an in-progress game (this may happen for partial guesses
         * especially, which do not represent part of a valid "game state").
         */
        fun getCachedGuess(): String

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Initial Configuration: Game Field
        //-----------------------------------------------------------------------------------------

        /**
         * Set fixed dimensions for the game field.
         */
        fun setGameFieldSize(length: Int, rows: Int)

        /**
         * Set fixed width for the game field, with unlimited rows.
         */
        fun setGameFieldUnlimited(length: Int)

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Initial Configuration: Code Characters
        //-----------------------------------------------------------------------------------------

        /**
         * Specify the language used for code words. This function will be called when
         * dealing with real word vocabularies.
         *
         * @param characters The characters allowed to appear in valid words.
         * @param locale The language/region for the language the codes are derived from.
         */
        fun setCodeLanguage(characters: Iterable<Char>, locale: Locale)

        /**
         * Specify the language used for code sequences. This function will be called when
         * dealing with arbitrary character sequences, e.g. "AAAA" or "AABC".
         *
         * @param characters The characters allowed to appear in valid codes.
         */
        fun setCodeComposition(characters: Iterable<Char>)

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Game State
        //-----------------------------------------------------------------------------------------

        /**
         * Update the current constraints for the Game.
         *
         * @param constraints Current constraints.
         * @param animate Animate this change; if false, apply immediately.
         */
        fun setConstraints(constraints: List<Constraint>, animate: Boolean = false)

        /**
         * Update the currently displayed guess for the Game.
         *
         * @param guess The guess to apply
         * @param animate Animate this change; if false, apply immediately.
         */
        fun setGuess(guess: String, animate: Boolean = false)

        /**
         * Update the currently displayed guess by REPLACING it with the
         * provided constraint. This is a simpler operation, easier to animate
         * and display, than fully resetting content with [setConstraints] and [setGuess]
         *
         * @param constraint The new constraint to apply
         * @param animate Animate this change; if false, apply immediately.
         */
        fun replaceGuessWithConstraint(constraint: Constraint, animate: Boolean = false)

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Constraint Analysis
        //-----------------------------------------------------------------------------------------

        /**
         * Provide per-character evaluations for display, such as in a keyboard format with colored
         * keys. The mapping may be incomplete; treat any omitted character as having no evaluation,
         * i.e. having markup "null" and maxCount of the code length.
         */
        fun setCharacterEvaluations(evaluations: Map<Char, CharacterEvaluation>)

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Player Prompts
        //-----------------------------------------------------------------------------------------

        /**
         * Prompt the player to enter a guess (after this is done, the View
         * should call [Presenter.onGuess]).
         *
         * @param suggestedGuess A suggestion for the next guess, perhaps partial.
         */
        fun promptForGuess(suggestedGuess: String?)

        /**
         * Prompt the player for an evaluation of the provided guess (after this is done,
         * the View should call [Presenter.onEvaluation].
         *
         * @param guess The guess to evaluate
         */
        fun promptForEvaluation(guess: String)

        /**
         * Prompt the user to wait for an update.
         */
        fun promptForWait()

        /**
         * The game is over! Show the player.
         *
         * @param uuid A unique identifier for the game outcome, for retrieving more information
         * if necessary.
         * @param solution The real code word for the game. If the player was the evaluator,
         * this may not be known (null)
         * @param rounds The number of complete rounds played (i.e. guesses evaluated,
         * constraints added).
         * @param solved Whether the code word was determined by the guesser.
         * @param playerVictory Whether the winner of the game (see [solved]) is the person
         * holding the device.
         */
        fun showGameOver(uuid: UUID, solution: String?, rounds: Int, solved: Boolean, playerVictory: Boolean)

        /**
         * Display an error to the user.
         *
         * @param error The type of error to display
         * @param guess The guess related to the error (if any)
         * @param violation For [ErrorType.CODE_NOT_CONSTRAINED] and [ErrorType.WORD_NOT_CONSTRAINED],
         * violation(s) detected with the candidate guess. Will be null for other [ErrorType]s.
         */
        fun showError(
            error: ErrorType,
            violations: List<Constraint.Violation>? = null
        )

        //-----------------------------------------------------------------------------------------
        //endregion
    }

    interface Presenter: BaseContract.Presenter<View> {

        //region Input from game settings changes
        //-----------------------------------------------------------------------------------------

        /**
         * The user has updated the GameSetup for this game in progress (in a way that is compatible
         * with its current game state).
         *
         * This function should be used if the updated game setup was not available from
         * [View.getUpdatedGameSetup] when the [Presenter] and [View] were originally connected;
         * otherwise it is redundant.
         *
         * @param gameSetup The updated GameSetup (which, as of this call, can also be accessed
         * with [View.getUpdatedGameSetup]).
         */
        fun onUpdatedGameSetup(gameSetup: GameSetup)

        /**
         * The user has forfeit the game, before a win or loss is registered. This decision is
         * permanent; the game will not proceed from this point and will not be reloaded later.
         */
        fun onForfeit()

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Input from the Guesser user
        //-----------------------------------------------------------------------------------------

        /**
         * The user is altering their guess (adding, inserting or removing characters).
         *
         * @param before The guess before this change
         * @param after The guess after this change
         */
        fun onGuessUpdated(before: String, after: String)

        /**
         * The user has entered a guess and wants it evaluated.
         */
        fun onGuess(guess: String)

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Input from the Evaluator user
        //-----------------------------------------------------------------------------------------

        /**
         * The user has entered a markup-style evaluation and wants it sent to the guesser.
         */
        fun onEvaluation(guess: String, markup: List<Constraint.MarkupType>)

        /**
         * The user has entered an exact/included count evaluation and wants it sent to the guesser.
         */
        fun onEvaluation(guess: String, exact: Int, included: Int)

        //-----------------------------------------------------------------------------------------
        //endregion

    }
}