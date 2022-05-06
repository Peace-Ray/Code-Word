package com.peaceray.codeword.domain.manager.game

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup

/**
 * Tracks "default" settings for games. Allows easy access to specific GameSetup fields
 * and metavalues (e.g. "hard mode") as well as persistence of keyed values; for instance,
 * the default Evaluator mode for a "CPU vs CPU" game type can be stored using a custom key
 * like "CPU_vs_CPU" and will not affect Evaluator values for any other keys.
 *
 * Care should be taken to limit the total number of keys used for default storage, as the Manager
 * does not clear its own contents. It's also worthwhile to consider that, while non-null defaults
 * will always be provided, they don't necessarily reflect previously stored settings and validity
 * should be considered in all combinations (some [GameSetup.Vocabulary]s require specific
 * [GameSetup.Evaluation] settings, for example, and the Defaults manager may provide invalid
 * combinations in some cases.
 */
interface GameDefaultsManager {

    //region Board Settings
    //---------------------------------------------------------------------------------------------
    val rounds: Int
    val nonzeroRounds: Int
    var board: GameSetup.Board
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Language Settings
    //---------------------------------------------------------------------------------------------
    val language: CodeLanguage
    var vocabulary: GameSetup.Vocabulary
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Difficulty Settings
    //---------------------------------------------------------------------------------------------
    var hardMode: Boolean
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Aggregate Setters
    //---------------------------------------------------------------------------------------------
    fun put(gameSetup: GameSetup)

    fun put(
        board: GameSetup.Board? = null,
        evaluation: GameSetup.Evaluation? = null,
        vocabulary: GameSetup.Vocabulary? = null,
        solver: GameSetup.Solver? = null,
        evaluator: GameSetup.Evaluator? = null
    )
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Keyed Defaults
    //---------------------------------------------------------------------------------------------
    fun get(key: String?): GameSetup

    fun getBoard(key: String?): GameSetup.Board
    fun getEvaluation(key: String?): GameSetup.Evaluation
    fun getVocabulary(key: String?): GameSetup.Vocabulary
    fun getSolver(key: String?): GameSetup.Solver
    fun getEvaluator(key: String?): GameSetup.Evaluator

    fun getRounds(key: String?, default: Int = 6): Int
    fun getNonzeroRounds(key: String?, default: Int = 6): Int
    fun getLanguage(key: String?, default: CodeLanguage = CodeLanguage.ENGLISH): CodeLanguage
    fun getHardMode(key: String?, default: Boolean = false): Boolean

    fun put(key: String?, gameSetup: GameSetup)

    fun put(
        key: String?,
        board: GameSetup.Board? = null,
        evaluation: GameSetup.Evaluation? = null,
        vocabulary: GameSetup.Vocabulary? = null,
        solver: GameSetup.Solver? = null,
        evaluator: GameSetup.Evaluator? = null
    )
    //---------------------------------------------------------------------------------------------
    //endregion

}