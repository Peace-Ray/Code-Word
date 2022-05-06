package com.peaceray.codeword.presentation.contracts

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup

/**
 * A Contract describing presenter-view interaction for creating a GameSetup. Depending on the
 * type of game desired, different game features may be available for configuration. For example,
 * games with non-human solvers or non-honest evaluators cannot be shared as Seeds (currently).
 */
interface GameSetupContract: BaseContract {

    enum class Type {
        DAILY,
        SEEDED,
        CUSTOM,
        ONGOING;
    }

    enum class Feature {
        SEED,
        PLAYER_ROLE,
        CODE_LANGUAGE,
        CODE_LENGTH,
        CODE_CHARACTERS,
        EVALUATOR_HONEST,
        HARD_MODE,
        ROUNDS,
        LAUNCH;
    }

    enum class Error {
        EXPIRED,
        COMPLETE,
        INVALID,
        NOT_ALLOWED,
        FEATURE_VALUE_NOT_ALLOWED;
    }

    enum class SessionProgress {
        NEW,
        ONGOING,
        WON,
        LOST;
    }

    data class GameSetupBundle(val seed: String?, val setup: GameSetup, val progress: SessionProgress) {
        constructor(setup: GameSetup, progress: SessionProgress): this(null, setup, progress)
        constructor(setup: GameSetup): this(null, setup, SessionProgress.NEW)
    }

    interface View: BaseContract.View {

        //region Actions
        //-----------------------------------------------------------------------------------------

        /**
         * Get the type of game currently selected.
         */
        fun getType(): Type

        /**
         * Return setup of an ongoing game. If [getType] is not [Type.ONGOING], return null.
         *
         * @return The setup of an ongoing game, if any.
         */
        fun getOngoingGameSetup(): Pair<String?, GameSetup>?

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Actions
        //-----------------------------------------------------------------------------------------

        /**
         * Close the View (?) with the provided GameSetup. Possibly launch the game, possibly
         * save settings, possibly apply settings changes to an ongoing game.
         *
         * @param seed The game seed, if any.
         * @param setup The GameSetup to launch.
         */
        fun finishGameSetup(seed: String?, setup: GameSetup)

        /**
         * Close the View (?), canceling setup.
         */
        fun cancelGameSetup()

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Values
        //-----------------------------------------------------------------------------------------

        /**
         * Set the current Seed, which may be visible (if not editable), and the GameSetup
         * (which will probably be represented across multiple views).
         */
        fun setGameSetup(seed: String?, gameSetup: GameSetup, progress: SessionProgress)

        /**
         * Show an error regarding user input of a given feature.
         */
        fun showError(feature: Feature, error: Error)

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Edit Permitted
        //-----------------------------------------------------------------------------------------

        /**
         * Sets the editable features; anything in the provided Collection is editable, anything
         * absent is not.
         */
        fun setFeatureAllowed(features: Collection<Feature>)

        /**
         * Set whether the user is permitted to alter this setting (may change over time
         * as other features are changed).
         */
        fun setFeatureAllowed(feature: Feature, allowed: Boolean)

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Values Permitted
        //-----------------------------------------------------------------------------------------

        /**
         * Set the available range for the integer feature (used for [Feature.CODE_LENGTH],
         * [Feature.CODE_CHARACTERS], and [Feature.ROUNDS]).
         */
        fun setFeatureValuesAvailable(feature: Feature, values: List<Int>)

        //-----------------------------------------------------------------------------------------
        //endregion
    }

    interface Presenter: BaseContract.Presenter<View> {

        //region View-level Actions
        //-----------------------------------------------------------------------------------------

        /**
         * The user has selected a particular type of game setup; this may result in wide-sweeping
         * changes to the available features and their values.
         */
        fun onTypeSelected(type: Type)

        /**
         * The user has clicked a button intending to launch a game.
         */
        fun onLaunchButtonClicked()

        /**
         * The user has clicked a button intending to cancel game setup.
         */
        fun onCancelButtonClicked()

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Seed Entry
        //-----------------------------------------------------------------------------------------

        /**
         * Notify the Presenter that the user has entered a new seed. Returns whether the View
         * should accept this change. Note that whatever the return value, additional calls may
         * be made by the Presenter, such as [View.setGameSetup] and [View.showError], either
         * during or after this call.
         *
         * @param seed The string entered by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted (if that hasn't been done automatically by the Presenter
         * during this call).
         */
        fun onSeedEntered(seed: String): Boolean

        /**
         * Notify the Presenter that the user has selected new player roles. Returns whether the View
         * should accept this change. Note that whatever the return value, additional calls may
         * be made by the Presenter, such as [View.setGameSetup] and [View.showError], either
         * during or after this call.
         *
         * @param solver The Solver selected by the user.
         * @param evaluator The Evaluator selected by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onRolesEntered(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): Boolean

        /**
         * Notify the Presenter that the user has selected a new language. Returns whether the View
         * should accept this change. Note that whatever the return value, additional calls may
         * be made by the Presenter, such as [View.setGameSetup] and [View.showError], either
         * during or after this call.
         *
         * @param language The language selected by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onLanguageEntered(language: CodeLanguage): Boolean

        /**
         * Notify the Presenter that the user has selected a new value for the indicated
         * Feature. It is preferred to use the Feature-specific functions where they exist.
         * Returns whether the View should accept this change. Note that whatever the return value,
         * additional calls may be made by the Presenter, such as [View.setGameSetup] and
         * [View.showError], either during or after this call.
         *
         * @param feature The Feature altered by the user.
         * @param active Whether the user has put this feature in an "active" setting
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onFeatureEntered(feature: Feature, active: Boolean): Boolean

        /**
         * Notify the Presenter that the user has selected a new value for the indicated
         * Feature. It is preferred to use the Feature-specific functions where they exist.
         * Returns whether the View should accept this change. Note that whatever the return value,
         * additional calls may be made by the Presenter, such as [View.setGameSetup] and
         * [View.showError], either during or after this call.
         *
         * @param feature The Feature altered by the user.
         * @param value The setting the user has selected for this feature.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onFeatureEntered(feature: Feature, value: Int): Boolean

        //-----------------------------------------------------------------------------------------
        //endregion
    }
}