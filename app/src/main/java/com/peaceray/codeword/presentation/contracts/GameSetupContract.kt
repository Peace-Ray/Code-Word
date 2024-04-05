package com.peaceray.codeword.presentation.contracts

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.datamodel.Information
import java.util.*

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

    enum class Qualifier(override val level: Information.Level): Information {
        VERSION_CHECK_PENDING(Information.Level.TIP),
        VERSION_CHECK_FAILED(Information.Level.ERROR),
        VERSION_UPDATE_AVAILABLE(Information.Level.TIP),
        VERSION_UPDATE_RECOMMENDED(Information.Level.WARN),
        VERSION_UPDATE_REQUIRED(Information.Level.ERROR),

        /**
         * For DAILY types only; indicates that the Daily puzzle is not Global, and should not be
         * represented or shared as such. This Qualifier should probably be ignored, if not
         * omitted entirely, from anything but Type.DAILY or Type.ONGOING for a Daily game.
         */
        LOCAL_DAILY(Information.Level.INFO);
    }

    enum class Feature {
        SEED,
        PLAYER_ROLE,
        CODE_LANGUAGE,
        CODE_LENGTH,
        CODE_CHARACTERS,
        CODE_CHARACTER_REPETITION,
        CODE_EVALUATION_POLICY,
        EVALUATOR_HONEST,
        HARD_MODE,
        ROUNDS,
        LAUNCH;
    }

    enum class Availability {
        /**
         * The specified [Feature] is available and can be edited.
         */
        AVAILABLE,

        /**
         * The specified [Feature] is locked, e.g. by the fact that the game is already
         * in-progress, but was available for configuration and should probably be displayed
         * in a non-editable state.
         */
        LOCKED,

        /**
         * The specified [Feature] is not available for change given other aspects of the game type.
         */
        DISABLED;
    }

    /**
     * An error originating from user action
     */
    enum class Error(override val level: Information.Level = Information.Level.ERROR): Information {
        /**
         * This is a timed game and cannot be launched as the time to play it has expired.
         */
        GAME_EXPIRED,

        /**
         * This is a timed game and cannot be launched as the time to play it has not arrived.
         */
        GAME_FORTHCOMING,

        /**
         * This game has already been completed and cannot be launched.
         */
        GAME_COMPLETE,

        /**
         * A feature has been edited for which edits are not permitted (e.g. changing the
         * secret length for the Daily).
         */
        FEATURE_NOT_ALLOWED,

        /**
         * An invalid value has been entered for the feature (e.g. "text" for a numeric field,
         * a seed with invalid formatting, etc.)
         */
        FEATURE_VALUE_INVALID,

        /**
         * A feature value has been entered which is outside the allowed range (e.g. a word
         * length for which no dictionary file exists).
         */
        FEATURE_VALUE_NOT_ALLOWED;
    }

    interface View: BaseContract.View {

        //region Actions
        //-----------------------------------------------------------------------------------------

        /**
         * Get the type of game currently selected.
         */
        fun getType(): Type

        /**
         * Get the Qualifier(s) for the type of game currently selected.
         */
        fun getQualifiers(): Set<Qualifier>

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
         * Close the View (?) with the provided GameStatusReview. Possibly launch the game, possibly
         * save settings, possibly apply settings changes to an ongoing game.
         *
         * @param review The GameStatusReview detailing seed, GameSetup, etc.
         */
        fun finishGameSetup(review: GameStatusReview)

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
        fun setGameStatusReview(review: GameStatusReview)

        /**
         * Show an error regarding user input of a given feature. If the error is explained by a
         * particular qualifier, it is specified.
         */
        fun showError(feature: Feature, error: Error, qualifier: Qualifier?)

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Code Characters
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

        //region Edit Permitted
        //-----------------------------------------------------------------------------------------

        /**
         * Sets the availability for all features. Any Feature not included in the provided mapping
         * has the [defaultAvailability], which should be 'null' if you want the previously set
         * availability to be maintained. Optionally, qualifiers may be specified to explain the
         * availability of each such Feature.
         *
         * @param availabilities: An incomplete mapping of Feature to Availability; missing Features
         * have [defaultAvailability].
         * @param qualifiers: An incomplete mapping of Feature to Qualifier; missing Features have
         * no Qualifier.
         * @param defaultAvailability The Availability applied to any Feature not present in
         * [availabilities.keys]. If 'null', the mapping is not changed.
         */
        fun setFeatureAvailability(
            availabilities: Map<Feature, Availability>,
            qualifiers: Map<Feature, Qualifier>,
            defaultAvailability: Availability? = Availability.DISABLED
        )

        /**
         * Set the availability of the specified feature. Optionally, specify a Qualifier to
         * explain its availability.
         *
         * @param feature The Feature in question
         * @param availability The Availability of the Feature
         * @param qualifier A Qualifier to explain the Feature's Availability, if any.
         */
        fun setFeatureAvailability(feature: Feature, availability: Availability, qualifier: GameSetupContract.Qualifier?)

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Values Permitted
        //-----------------------------------------------------------------------------------------

        /**
         * Set the available range for an integer feature (used for [Feature.CODE_LENGTH],
         * [Feature.CODE_CHARACTERS], and [Feature.ROUNDS]).
         */
        fun setFeatureValuesAllowed(feature: Feature, values: List<Int>)

        /**
         * Set the available [CodeLanguage]s for secrets.
         */
        fun setLanguagesAllowed(languages: List<CodeLanguage>)

        /**
         * Set the available [ConstraintPolicy]s for guess evaluation.
         */
        fun setEvaluationPoliciesAllowed(policies: List<ConstraintPolicy>)

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
        fun onTypeSelected(type: Type, qualifiers: Set<Qualifier> = emptySet())

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
         * be made by the Presenter, such as [View.setGameStatusReview] and [View.showError], either
         * during or after this call.
         *
         * @param seed The string entered by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted (if that hasn't been done automatically by the Presenter
         * during this call).
         */
        fun onSeedEntered(seed: String): Boolean

        /**
         * Notify the Presenter that the user has requested a new seed randomization. This is not
         * a fresh GameSetup, just a new randomized seed with otherwise the same settings. It is
         * the responsibility of the Presenter to generate the new seed, but for consistency,
         * this function returns a Boolean indicating if the change is "accepted" (and the Presenter
         * will perform the randomization).
         *
         * @return Whether this action is accepted by the Presenter. If 'false',
         * some indication that seed randomization is not possible is appropriate.
         */
        fun onSeedRandomized(): Boolean

        /**
         * Notify the Presenter that the user has selected new player roles. Returns whether the View
         * should accept this change. Note that whatever the return value, additional calls may
         * be made by the Presenter, such as [View.setGameStatusReview] and [View.showError], either
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
         * be made by the Presenter, such as [View.setGameStatusReview] and [View.showError], either
         * during or after this call.
         *
         * @param language The language selected by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onLanguageEntered(language: CodeLanguage): Boolean

        /**
         * Notify the Presenter that the user has selected a new ConstraintPolicy. Returns whether
         * the View should accept this change. Note that whatever the return value, additional
         * calls may be made by the Presenter, such as [View.setGameStatusReview] and [View.showError],
         * either during or after this call.
         *
         * @param policy The ConstraintPolicy selected by the user.
         * @return Whether this change should be accepted (left in place) by the View. If 'false',
         * the change should be reverted.
         */
        fun onConstraintPolicyEntered(policy: ConstraintPolicy): Boolean

        /**
         * Notify the Presenter that the user has selected a new value for the indicated
         * Feature. It is preferred to use the Feature-specific functions where they exist.
         * Returns whether the View should accept this change. Note that whatever the return value,
         * additional calls may be made by the Presenter, such as [View.setGameStatusReview] and
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
         * additional calls may be made by the Presenter, such as [View.setGameStatusReview] and
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