package com.peaceray.codeword.domain.manager.game.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameDefaultsManager
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.glue.ForApplication
import dagger.hilt.android.scopes.ActivityScoped
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameDefaultsManagerImpl @Inject constructor(
    @ForApplication val context: Context,
): GameDefaultsManager {

    //region SharedPreferences
    //---------------------------------------------------------------------------------------------
    private companion object {
        const val PREFS_FILENAME = "GAME_DEFAULTS_MANAGER_PREFERENCES"
        // board
        const val KEY_BOARD_ROUNDS = "ROUNDS"
        const val KEY_BOARD_NONZERO_ROUNDS = "NONZERO_ROUNDS"
        // evaluation
        const val KEY_EVALUATION_TYPE = "EVALUATION_TYPE"
        const val KEY_EVALUATION_ENFORCED = "EVALUATION_ENFORCED"
        // vocabulary
        const val KEY_VOCABULARY_LANGUAGE = "LANGUAGE"
        const val KEY_VOCABULARY_TYPE = "VOCABULARY_TYPE"
        const val KEY_VOCABULARY_LENGTH = "VOCABULARY_LENGTH"
        const val KEY_VOCABULARY_CHARACTERS = "VOCABULARY_CHARACTERS"
        // players
        const val KEY_SOLVER = "SOLVER"
        const val KEY_EVALUATOR = "EVALUATOR"
        // metavalues
        const val KEY_HARD_MODE = "HARD_MODE"
    }

    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)
    }

    private fun getPreferences(key: String?): SharedPreferences {
        return if (key == null) preferences else {
            context.getSharedPreferences("${PREFS_FILENAME}_$key", Context.MODE_PRIVATE)
        }
    }

    //region SharedPreferences: Enums
    private inline fun <reified T: Enum<T>> SharedPreferences.getEnum(key: String): T? {
        val storedName = getString(key, null)
        return if (storedName == null) null else {
            try {
                enumValueOf<T>(storedName)
            } catch (err: Exception) {
                Timber.e(err, "Persisted $key setting $storedName not recognized")
                null
            }
        }
    }

    private inline fun <reified T: Enum<T>> SharedPreferences.getEnum(key: String, defaultValue: T): T {
        val storedName = getString(key, null)
        return if (storedName == null) defaultValue else {
            try {
                enumValueOf<T>(storedName)
            } catch (err: Exception) {
                Timber.e(err, "Persisted $key setting $storedName not recognized")
                defaultValue
            }
        }
    }

    private inline fun <reified T: Enum<T>> SharedPreferences.Editor.putEnum(
        key: String,
        value: T?
    ): SharedPreferences.Editor {
        putString(key, value?.name)
        return this
    }
    //endregion

    //region SharedPreferences: Board
    private fun SharedPreferences.getRounds(default: Int = 6) = getInt(KEY_BOARD_ROUNDS, default)

    private fun SharedPreferences.getNonzeroRounds(default: Int = 6) =
        getInt(KEY_BOARD_NONZERO_ROUNDS, default)

    private fun SharedPreferences.getBoard() = GameSetup.Board(getRounds())

    private fun SharedPreferences.Editor.putBoard(board: GameSetup.Board) {
        if (board.rounds > 0) putInt(KEY_BOARD_NONZERO_ROUNDS, board.rounds)
        putInt(KEY_BOARD_ROUNDS, board.rounds)
    }
    //endregion

    //region SharedPreferences: Evaluation
    private fun SharedPreferences.getEvaluation() = GameSetup.Evaluation(
        getEnum(KEY_EVALUATION_TYPE, ConstraintPolicy.PERFECT),
        getEnum(KEY_EVALUATION_ENFORCED, ConstraintPolicy.IGNORE)
    )

    private fun SharedPreferences.getHardMode(default: Boolean = false) = getBoolean(KEY_HARD_MODE, default)

    private fun SharedPreferences.Editor.putEvaluation(evaluation: GameSetup.Evaluation) {
        putEnum(KEY_EVALUATION_TYPE, evaluation.type)
        putEnum(KEY_EVALUATION_ENFORCED, evaluation.enforced)
        putBoolean(KEY_HARD_MODE, evaluation.enforced != ConstraintPolicy.IGNORE)
    }

    private fun SharedPreferences.Editor.putHardMode(value: Boolean) = putBoolean(KEY_HARD_MODE, value)
    //endregion

    //region SharedPreferences: Vocabulary
    private fun SharedPreferences.getLanguage(default: CodeLanguage = CodeLanguage.ENGLISH) =
        getEnum(KEY_VOCABULARY_LANGUAGE, default)

    private fun SharedPreferences.getVocabulary() = GameSetup.Vocabulary(
        getLanguage(),
        getEnum(KEY_VOCABULARY_TYPE, GameSetup.Vocabulary.VocabularyType.LIST),
        getInt(KEY_VOCABULARY_LENGTH, 5),
        getInt(KEY_VOCABULARY_CHARACTERS, 26)
    )

    private fun SharedPreferences.Editor.putVocabulary(value: GameSetup.Vocabulary) {
        putEnum(KEY_VOCABULARY_LANGUAGE, value.language)
        putEnum(KEY_VOCABULARY_TYPE, value.type)
        putInt(KEY_VOCABULARY_LENGTH, value.length)
        putInt(KEY_VOCABULARY_CHARACTERS, value.characters)
    }
    //endregion

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Board Settings
    //---------------------------------------------------------------------------------------------
    override val rounds get() = preferences.getRounds()
    override val nonzeroRounds get() = preferences.getNonzeroRounds()
    override var board: GameSetup.Board
        get() = preferences.getBoard()
        set(value) = preferences.edit { putBoard(value) }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Language Settings
    //---------------------------------------------------------------------------------------------
    override val language get() = preferences.getLanguage()
    override var vocabulary
        get() = preferences.getVocabulary()
        set(value) = preferences.edit { putVocabulary(value) }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Difficulty Settings
    //---------------------------------------------------------------------------------------------
    override var hardMode
        get() = preferences.getHardMode()
        set(value) = preferences.edit { putHardMode(value) }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Aggregate Setters
    //---------------------------------------------------------------------------------------------
    override fun put(gameSetup: GameSetup) = put(null, gameSetup)

    override fun put(
        board: GameSetup.Board?,
        evaluation: GameSetup.Evaluation?,
        vocabulary: GameSetup.Vocabulary?,
        solver: GameSetup.Solver?,
        evaluator: GameSetup.Evaluator?,
    ) = put(null, board, evaluation, vocabulary, solver, evaluator)
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Keyed Defaults
    //---------------------------------------------------------------------------------------------
    override fun get(key: String?) = getPreferences(key).let { GameSetup(
        it.getBoard(),
        it.getEvaluation(),
        it.getVocabulary(),
        it.getEnum(KEY_SOLVER, GameSetup.Solver.PLAYER),
        it.getEnum(KEY_EVALUATOR, GameSetup.Evaluator.HONEST),
        GameSetup.createSeed(),
        daily = false,
        version = 0
    ) }

    override fun getBoard(key: String?) = getPreferences(key).getBoard()
    override fun getEvaluation(key: String?) = getPreferences(key).getEvaluation()
    override fun getVocabulary(key: String?) = getPreferences(key).getVocabulary()
    override fun getSolver(key: String?) = getPreferences(key).getEnum(KEY_SOLVER, GameSetup.Solver.PLAYER)
    override fun getEvaluator(key: String?) = getPreferences(key).getEnum(KEY_EVALUATOR, GameSetup.Evaluator.HONEST)

    override fun getRounds(key: String?, default: Int) = getPreferences(key).getRounds(default)
    override fun getNonzeroRounds(key: String?, default: Int) = getPreferences(key).getNonzeroRounds(default)
    override fun getLanguage(key: String?, default: CodeLanguage) = getPreferences(key).getLanguage(default)
    override fun getHardMode(key: String?, default: Boolean) = getPreferences(key).getHardMode(default)

    override fun put(key: String?, gameSetup: GameSetup) = put(
        key,
        board = gameSetup.board,
        evaluation = gameSetup.evaluation,
        vocabulary = gameSetup.vocabulary,
        solver = gameSetup.solver,
        evaluator = gameSetup.evaluator
    )

    override fun put(
        key: String?,
        board: GameSetup.Board?,
        evaluation: GameSetup.Evaluation?,
        vocabulary: GameSetup.Vocabulary?,
        solver: GameSetup.Solver?,
        evaluator: GameSetup.Evaluator?
    ) = getPreferences(key).edit {
        if (board != null) putBoard(board)
        if (evaluation != null) putEvaluation(evaluation)
        if (vocabulary != null) putVocabulary(vocabulary)
        if (solver != null) putEnum(KEY_SOLVER, solver)
        if (evaluator != null) putEnum(KEY_EVALUATOR, evaluator)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}