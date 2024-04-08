package com.peaceray.codeword.data.manager.game.setup

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType

/**
 * A Manager for retrieving [GameSetup] objects describing the active game.
 * They may be situation-dependent, describe the default game, etc. The most basic call
 * is [getSetup] which in practice should provide the session information most directly
 * relevant to the calling context, possibly set using persisted settings.
 */
interface GameSetupManager {

    //region Validation / Versioning
    //---------------------------------------------------------------------------------------------

    /**
     * The "Era" of a seed string. As app updates appear and game types are added or changed,
     * seeds that originate from a different version of the app may need special treatment by
     * this Manager or other classes.
     */
    enum class SeedEra {
        /**
         * The current "era": a native seed for this app.
         */
        CURRENT,

        /**
         * A legacy seed: generated by a previous version of the app, but still
         * supported.
         */
        LEGACY,

        /**
         * A seed generated by a previous version of the app; no longer supported.
         */
        RETIRED,

        /**
         * A seed that appears to be generated by a _future_ version of the app;
         * i.e. the formatting seems to match expectations, but with embedded versioning
         * indicating an as-yet-unknown version.
         */
        FUTURISTIC,

        /**
         * A seed that may be malformed, mis-transcribed, invalid, or from a future
         * version that alters the fundamental structure of a seed s.t. it cannot
         * be recognized as a "future" seed.
         */
        UNKNOWN;
    }
    
    /**
     * Return the [SeedEra] of the provided [seed]. Note that even a [SeedEra.CURRENT]
     * seed may still be invalid; this function only evaluates the seed to the point that
     * an era can be inferred (e.g. it may check only the first N characters of the seed).
     *
     * @param seed The seed to evaluate.
     * @return The SeedEra of the seed.
     */
    fun getSeedEra(seed: String): SeedEra

    /**
     * Return the [SeedEra] of the provided [gameSetup], which may have been created from
     * a seed string, loaded as a saved game, etc. Any [GameSetup] created by this Manager
     * from non-legacy inputs will have [SeedEra.CURRENT] era.
     *
     * SeedEra may be altered by calls to [modifyGameSetup].
     *
     * @param gameSetup The GameSetup to evaluate.
     * @return The SeedEra of the GameSetup.
     */
    fun getSeedEra(gameSetup: GameSetup): SeedEra

    /**
     * Return the current SeedVersion, as an integer. This is the maximum version the
     * Manager is capable of setting on a constructed [GameSetup] object.
     *
     * Note that [getSetup] may provide objects with lower SeedVersions, depending on
     * arguments or context, but getSetup(hard: Boolean) should always use the most
     * current available version.
     */
    fun getSeedVersion(): Int

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Creating GameSetups
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
     * Creates a [GameSetup] for the specified [GameType].
     *
     * @param type The game type to expand into a GameSetup
     * @param hard Whether to play in "hard mode" (if omitted, use local setting default)
     */
    fun getSetup(type: GameType, hard: Boolean = false): GameSetup

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Examining GameSetups
    //---------------------------------------------------------------------------------------------

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

    /**
     * Returns whether this GameSetup represents a Daily puzzle (this is explicitly represented
     * by the GameSetup object itself).
     */
    fun isDaily(setup: GameSetup): Boolean

    /**
     * Returns whether this GameSetup represent a Daily puzzle or its equivalent -- "Daily" status
     * is explicitly represented by the GameSetup object itself, but it is possible to encode a
     * Daily game in a Setup not flagged as such. This call attempts to recognize such a case.
     */
    fun isDailyOrEquivalent(setup: GameSetup): Boolean

    /**
     * Infers the [GameType] of the provided Game Setup.
     */
    fun getType(setup: GameSetup): GameType

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Code Language Setup
    //---------------------------------------------------------------------------------------------

    /**
     * Retrieve CodeLanguage details for the provided language, including recommendations for
     * code lengths and character counts.
     */
    fun getCodeLanguageDetails(language: CodeLanguage): CodeLanguageDetails

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Difficulty Setup
    //---------------------------------------------------------------------------------------------

    /**
     * Retrieve recommendations for the number of rounds a gome with this vocabulary should be played.
     *
     * @param vocabulary The game Vocabulary
     * @param evaluation The game Evaluation
     * @return A 2-tuple of the recommended rounds, and the recommended maximum number of rounds.
     */
    fun getRecommendedRounds(vocabulary: GameSetup.Vocabulary, evaluation: GameSetup.Evaluation? = null): Pair<Int, Int>

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Game Modification
    //---------------------------------------------------------------------------------------------

    /**
     * Create and return a GameSetup instance by replacing fields of the given [setup] with
     * whatever modified components are specified. This function does not simply return a newly
     * constructed [GameSetup] instance as with [GameSetup.with]; it takes the
     * original [GameSetup], applies any explicitly specified modifications, and attempts to
     * sanitize all other values so the resulting GameSetup object is still supported for play.
     * This might require altering word length, character sets, etc. If no sanitized and
     * supported GameSetup can be created, throws an [UnsupportedOperationException].
     *
     * This function will attempt to maintain the current version of the GameSetup, on the
     * assumption that (if not default) it was created from a saved game or manually entered
     * seed, but may alter the version to the current default if necessary to support the
     * specified modifications.
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
     * @param daily Set whether the GameSetup is a Daily, or just some other type of puzzle
     * @param randomized Randomize the seed value w/o affecting game settings.
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
        randomized: Boolean? = null
    ): GameSetup

    //---------------------------------------------------------------------------------------------
    //endregion

}