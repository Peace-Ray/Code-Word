package com.peaceray.codeword.presentation.contracts

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import java.util.*

interface CodeGameContract: BaseContract {
    interface View: BaseContract.View {
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
         */
        fun promptForGuess()

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

        //-----------------------------------------------------------------------------------------
        //endregion


        //region Player Prompts
        //-----------------------------------------------------------------------------------------

        /**
         * The game is over! Show the player.
         *
         * @param solution The real code word for the game. If the player was the evaluator,
         * this may not be known (null)
         * @param rounds The number of complete rounds played (i.e. guesses evaluated,
         * constraints added).
         * @param solved Whether the code word was determined by the guesser.
         * @param playerVictory Whether the winner of the game (see [solved]) is the person
         * holding the device.
         */
        fun showGameOver(solution: String?, rounds: Int, solved: Boolean, playerVictory: Boolean)

        //-----------------------------------------------------------------------------------------
        //endregion
    }

    interface Presenter: BaseContract.Presenter<View> {
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