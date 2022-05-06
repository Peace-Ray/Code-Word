package com.peaceray.codeword.domain.manager.game

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.data.model.game.GameSetup

/**
 * A Manager for retrieving [GameSetup] objects describing the active game.
 * They may be situation-dependent, describe the default game, etc. The most basic call
 * is [getSetup] which in practice should provide the session information most directly
 * relevant to the calling context, possibly set using persisted settings.
 */
interface GameSetupManager {

    //region Game Setup
    //---------------------------------------------------------------------------------------------

    /**
     * Returns an appropriate GameSetup for current settings
     */
    fun getSetup(hard: Boolean = false): GameSetup

    /**
     * Creates the [GameSetup] for a daily challenge. Does NOT check whether the daily challenge
     * has been attempted yet.
     */
    fun getDailySetup(hard: Boolean = false): GameSetup

    /**
     * Parses the provided string, returning the appropriate GameSetup. Throws for seed strings
     * that cannot be parsed as valid GameSetups.
     *
     * @param seed The seed string to parse into a GameSetup
     * @param hard Whether to play in "hard mode" (if omitted, use
     * local setting default).
     */
    fun getSetup(seed: String, hard: Boolean = false): GameSetup

    /**
     * Encodes the provided GameSetup as a seed, if possible (not all setups may be seeded).
     *
     * For valid GameSetups this function will return either a seed string from which it can
     * be recreated, or null for non-seeded games (e.g. for games with BOT solvers). Will
     * only throw an exception if the GameSetup itself is malformed.
     */
    fun getSeed(setup: GameSetup): String?

    /**
     * Returns whether this GameSetup is in "Hard Mode". Hard mode is not easy to read directly
     * from GameSetup as it is represented indirectly across several values.
     */
    fun isHard(setup: GameSetup): Boolean

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Game Modification
    //---------------------------------------------------------------------------------------------

    /**
     * Modify the GameSetup provided by applying whatever modified components are specified.
     * This function does not simply return a newly constructed GameSetup object; it takes the
     * original [GameSetup], applies any explicitly specified modifications, and attempts to
     * sanitize all other values so the resulting GameSetup object is still supported. This might
     * require altering word length, character sets, etc. If no sanitized and supported GameSetup
     * can be created, throws an [UnsupportedOperationException].
     *
     * @param setup The setup to modify
     * @param board The new board size, if any
     * @param evaluation The new evaluation mechanic
     * @param vocabulary The new vocabulary to use
     * @param solver The new solver type
     * @param evaluator The new evaluator type
     * @param hard Hard mode setting
     * @param seed The seed string
     * @param language The vocabulary language to use (other Vocabulary settings will be inferred)
     */
    @Throws(UnsupportedOperationException::class)
    fun modifyGameSetup(
        setup: GameSetup,
        board: GameSetup.Board? = null,
        evaluation: GameSetup.Evaluation? = null,
        vocabulary: GameSetup.Vocabulary? = null,
        solver: GameSetup.Solver? = null,
        evaluator: GameSetup.Evaluator? = null,
        hard: Boolean? = null,
        seed: String? = null,
        language: CodeLanguage? = null,
    ): GameSetup

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Code Language Setup
    //---------------------------------------------------------------------------------------------

    fun getCodeLanguageDetails(language: CodeLanguage): CodeLanguageDetails

    //---------------------------------------------------------------------------------------------
    //endregion

}