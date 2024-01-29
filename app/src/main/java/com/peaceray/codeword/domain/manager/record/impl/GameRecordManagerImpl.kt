package com.peaceray.codeword.domain.manager.record.impl

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.*
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.domain.manager.record.GameRecordManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.utils.histogram.IntHistogram
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of GameRecordManager that uses low-level SQLite database calls to implement
 * record persistence.
 *
 * TODO Consider replacing low-level calls with persistence middleware such as Room.
 */
@Singleton
class GameRecordManagerImpl @Inject constructor(
    @ForApplication val context: Context,
    val gameSetupManager: GameSetupManager
): GameRecordManager {

    //region Database Helpers
    //---------------------------------------------------------------------------------------------
    object GameRecordContract {
        // Base Class
        interface Entry {
            val TABLE_NAME: String
            val COLUMNS: Array<String>
            val SQL_CREATE_TABLE: String
        }

        // Game Outcome Record Table
        object GameOutcomeEntry: Entry {
            override val TABLE_NAME = "game_outcome"
            const val COLUMN_NAME_GAME_UUID = "game_uuid"
            const val COLUMN_NAME_GAME_TYPE = "game_type"
            const val COLUMN_NAME_DAILY = "daily"
            const val COLUMN_NAME_HARD = "hard"
            const val COLUMN_NAME_SOLVER_ROLE = "solver_role"
            const val COLUMN_NAME_EVALUATOR_ROLE = "evaluator_role"
            const val COLUMN_NAME_SEED = "seed"
            const val COLUMN_NAME_OUTCOME = "outcome"
            const val COLUMN_NAME_CURRENT_ROUND = "current_round"
            const val COLUMN_NAME_CONSTRAINTS = "constraints"
            const val COLUMN_NAME_GUESS = "guess"
            const val COLUMN_NAME_SECRET = "secret"
            const val COLUMN_NAME_ROUNDS = "rounds"
            const val COLUMN_NAME_RECORDED_AT = "recorded_at"

            override val COLUMNS = arrayOf(
                BaseColumns._ID,
                COLUMN_NAME_GAME_UUID,
                COLUMN_NAME_GAME_TYPE,
                COLUMN_NAME_DAILY,
                COLUMN_NAME_HARD,
                COLUMN_NAME_SOLVER_ROLE,
                COLUMN_NAME_EVALUATOR_ROLE,
                COLUMN_NAME_SEED,
                COLUMN_NAME_OUTCOME,
                COLUMN_NAME_CURRENT_ROUND,
                COLUMN_NAME_CONSTRAINTS,
                COLUMN_NAME_GUESS,
                COLUMN_NAME_SECRET,
                COLUMN_NAME_ROUNDS,
                COLUMN_NAME_RECORDED_AT
            )

            override val SQL_CREATE_TABLE =
                "CREATE TABLE $TABLE_NAME (" +
                        "${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        "$COLUMN_NAME_GAME_UUID TEXT NOT NULL UNIQUE," +
                        "$COLUMN_NAME_GAME_TYPE TEXT NOT NULL," +
                        "$COLUMN_NAME_DAILY INTEGER NOT NULL," +
                        "$COLUMN_NAME_HARD INTEGER NOT NULL," +
                        "$COLUMN_NAME_SOLVER_ROLE TEXT NOT NULL," +
                        "$COLUMN_NAME_EVALUATOR_ROLE TEXT NOT NULL," +
                        "$COLUMN_NAME_SEED TEXT," +
                        "$COLUMN_NAME_OUTCOME TEXT NOT NULL," +
                        "$COLUMN_NAME_CURRENT_ROUND INTEGER NOT NULL," +
                        "$COLUMN_NAME_CONSTRAINTS TEXT NOT NULL," +
                        "$COLUMN_NAME_GUESS TEXT," +
                        "$COLUMN_NAME_SECRET TEXT," +
                        "$COLUMN_NAME_ROUNDS INTEGER NOT NULL," +
                        "$COLUMN_NAME_RECORDED_AT BIGINT NOT NULL" +
                        ")"
        }

        // Game Type Performance Record Table
        object GameTypePerformanceEntry: Entry, BaseColumns {
            override val TABLE_NAME = "game_type_performance"
            const val COLUMN_NAME_GAME_TYPE = "game_type"
            const val COLUMN_NAME_DAILY = "daily"
            const val COLUMN_NAME_SOLVER_ROLE = "solver_role"
            const val COLUMN_NAME_EVALUATOR_ROLE = "evaluator_role"
            const val COLUMN_NAME_WINNING_TURN_COUNTS = "winning_turn_counts"
            const val COLUMN_NAME_LOSING_TURN_COUNTS = "losing_turn_counts"
            const val COLUMN_NAME_FORFEIT_TURN_COUNTS = "forfeit_turn_counts"

            const val COLUMN_VALUE_GAME_TYPE_ALL = "all"

            override val COLUMNS = arrayOf(
                BaseColumns._ID,
                COLUMN_NAME_GAME_TYPE,
                COLUMN_NAME_DAILY,
                COLUMN_NAME_SOLVER_ROLE,
                COLUMN_NAME_EVALUATOR_ROLE,
                COLUMN_NAME_WINNING_TURN_COUNTS,
                COLUMN_NAME_LOSING_TURN_COUNTS,
                COLUMN_NAME_FORFEIT_TURN_COUNTS
            )

            override val SQL_CREATE_TABLE =
                "CREATE TABLE $TABLE_NAME (" +
                        "${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                        "$COLUMN_NAME_GAME_TYPE TEXT NOT NULL," +
                        "$COLUMN_NAME_DAILY INTEGER NOT NULL," +
                        "$COLUMN_NAME_SOLVER_ROLE TEXT NOT NULL," +
                        "$COLUMN_NAME_EVALUATOR_ROLE TEXT NOT NULL," +
                        "$COLUMN_NAME_WINNING_TURN_COUNTS TEXT NOT NULL," +
                        "$COLUMN_NAME_LOSING_TURN_COUNTS TEXT NOT NULL," +
                        "$COLUMN_NAME_FORFEIT_TURN_COUNTS TEXT NOT NULL" +
                        ")"
        }

        // Player Streak
        object PlayerStreakEntry: Entry, BaseColumns {
            override val TABLE_NAME = "player_streak"
            const val COLUMN_NAME_GAME_TYPE = "game_type"
            const val COLUMN_NAME_STREAK = "streak"
            const val COLUMN_NAME_BEST_STREAK = "best_streak"
            const val COLUMN_NAME_DAILY_STREAK = "daily_streak"
            const val COLUMN_NAME_BEST_DAILY_STREAK = "best_daily_streak"

            override val COLUMNS = arrayOf(
                BaseColumns._ID,
                COLUMN_NAME_GAME_TYPE,
                COLUMN_NAME_STREAK,
                COLUMN_NAME_BEST_STREAK,
                COLUMN_NAME_DAILY_STREAK,
                COLUMN_NAME_BEST_DAILY_STREAK
            )

            override val SQL_CREATE_TABLE =
                "CREATE TABLE $TABLE_NAME (" +
                "${BaseColumns._ID} INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL," +
                "$COLUMN_NAME_GAME_TYPE TEXT NOT NULL UNIQUE," +
                "$COLUMN_NAME_STREAK INTEGER DEFAULT 0," +
                "$COLUMN_NAME_BEST_STREAK INTEGER DEFAULT 0," +
                "$COLUMN_NAME_DAILY_STREAK INTEGER DEFAULT 0," +
                "$COLUMN_NAME_BEST_DAILY_STREAK INTEGER DEFAULT 0" +
                ")"
        }
    }

    class GameRecordDbHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase?) {
            db?.execSQL("${GameRecordContract.GameOutcomeEntry.SQL_CREATE_TABLE};")
            db?.execSQL("${GameRecordContract.GameTypePerformanceEntry.SQL_CREATE_TABLE};")
            db?.execSQL("${GameRecordContract.PlayerStreakEntry.SQL_CREATE_TABLE};")
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            if (oldVersion == 1) {
                // after this version, GameTypes use an expanded string representation that
                // specifies ConstraintPolicy and letter occurrences. Convert rows to the new format.
                val updateGameType: (table: String, column: String, omit: String) -> Unit = { table, column, omit ->
                    val queryGameTypes: () -> List<String> = {
                        val types = mutableListOf<String>()
                        db?.query(
                            true, table, arrayOf(column), "$column != ?", arrayOf(omit),
                            null, null, null, null
                        )?.use {
                            if (it.moveToFirst()) {
                                do {
                                    types.add(it.getString(0))
                                    it.moveToNext()
                                } while (!it.isAfterLast)
                            }
                        }
                        types.toList()
                    }

                    queryGameTypes().forEach { legacy ->
                        val gameType = GameType.fromString(legacy)
                        if (legacy != gameType.toString()) {
                            // updated string representation available
                            val cv = ContentValues()
                            cv.put(column, gameType.toString())
                            Timber.v("DB update v$oldVersion $table put $cv where $column = ?, ($legacy)")
                            db?.updateWithOnConflict(table, cv, "$column = ?", arrayOf(legacy), SQLiteDatabase.CONFLICT_IGNORE)
                        }
                    }

                    queryGameTypes().forEach { Timber.v("DB updated v$oldVersion $table put $column = $it") }
                }

                GameRecordContract.GameOutcomeEntry
                    .run { updateGameType(TABLE_NAME, COLUMN_NAME_GAME_TYPE, "") }
                GameRecordContract.GameTypePerformanceEntry
                    .run { updateGameType(TABLE_NAME, COLUMN_NAME_GAME_TYPE, COLUMN_VALUE_GAME_TYPE_ALL) }
                GameRecordContract.PlayerStreakEntry
                    .run { updateGameType(TABLE_NAME, COLUMN_NAME_GAME_TYPE, "") }
            }
        }

        companion object {
            const val DATABASE_NAME = "game_record.db"
            const val DATABASE_VERSION = 2
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameRecordManager
    //---------------------------------------------------------------------------------------------
    private val dbHelper: GameRecordDbHelper by lazy { GameRecordDbHelper(context) }

    override fun record(seed: String?, setup: GameSetup, game: Game, secret: String?) {
        record(GameSaveData(seed, setup, game), secret)
    }

    override fun record(gameSaveData: GameSaveData, secret: String?) {
        Timber.d("record")
        val gameType = gameSetupManager.getType(gameSaveData.setup)

        val gameEntry = GameRecordContract.GameOutcomeEntry
        val streakEntry = GameRecordContract.PlayerStreakEntry
        synchronized(this) {
            // Multiple table updates to make. The game outcome itself must be written or
            // updated. The PerformanceRecord table must be updated twice -- once for "all puzzles",
            // once for the specific game type played. If the game itself had previously been
            // recorded, the PerformanceRecord update must have the previously recorded turn count
            // decremented as the new turn count is incremented. Finally, update the
            // player streak for the game type.

            // Insert or update the game outcome; keep the previous value (if any) for performance
            // record increments.
            val previousOutcome = getOutcome(gameSaveData.uuid)
            if (previousOutcome == null) {
                Timber.d("Inserting game outcome")
                dbHelper.writableDatabase.insertWithOnConflict(
                    gameEntry.TABLE_NAME,
                    "",
                    putValues(ContentValues(), gameSaveData, secret),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
            } else {
                Timber.d("Updating game outcome")
                dbHelper.writableDatabase.update(
                    gameEntry.TABLE_NAME,
                    putValues(ContentValues(), gameSaveData, secret),
                    "${gameEntry.COLUMN_NAME_GAME_UUID} = ?",
                    arrayOf(gameSaveData.uuid.toString())
                )
            }

            // Update performance record for this and "all puzzles"
            recordGamePerformance(previousOutcome, gameSaveData, gameType)

            // Record player streak ONLY if the player guessed and their opponent was honest,
            // and only once per game (unless transforming a win into a loss automagically...?)
            if (
                gameSaveData.setup.solver == GameSetup.Solver.PLAYER &&
                gameSaveData.setup.evaluator == GameSetup.Evaluator.HONEST &&
                (previousOutcome == null || gameSaveData.lost)
            ) {
                // create if not extent (ignore conflict)
                val streakCV = ContentValues()
                streakCV.put(streakEntry.COLUMN_NAME_GAME_TYPE, gameType.toString())
                dbHelper.writableDatabase.insertWithOnConflict(
                    streakEntry.TABLE_NAME,
                    "",
                    streakCV,
                    SQLiteDatabase.CONFLICT_IGNORE
                )

                // set relevant columns
                val sql = streakEntry.run {
                    val updates = mutableListOf<String>()
                    if (gameSaveData.won) {
                        updates.add("$COLUMN_NAME_BEST_STREAK = max($COLUMN_NAME_BEST_STREAK, $COLUMN_NAME_STREAK + 1)")
                        updates.add("$COLUMN_NAME_STREAK = $COLUMN_NAME_STREAK + 1")
                        if (gameSaveData.setup.daily) {
                            updates.add("$COLUMN_NAME_BEST_DAILY_STREAK = max($COLUMN_NAME_BEST_DAILY_STREAK, $COLUMN_NAME_DAILY_STREAK + 1)")
                            updates.add("$COLUMN_NAME_DAILY_STREAK = $COLUMN_NAME_DAILY_STREAK + 1")
                        }
                    } else {
                        updates.add("$COLUMN_NAME_STREAK = 0")
                        if (gameSaveData.setup.daily) {
                            updates.add("$COLUMN_NAME_DAILY_STREAK = 0")
                        }
                    }

                    "UPDATE $TABLE_NAME SET ${updates.joinToString(",")} WHERE $COLUMN_NAME_GAME_TYPE = ?;"
                }

                val stmt = dbHelper.writableDatabase.compileStatement(sql)
                stmt.bindString(1, gameType.toString())
                val rows = stmt.executeUpdateDelete()
                if (rows != 1) Timber.e("Failed to create or update ${streakEntry.TABLE_NAME} row")
            }
        }
    }

    private fun recordGamePerformance(
        previousOutcome: GameOutcome?,
        gameSaveData: GameSaveData,
        gameType: GameType
    ) {
        val perfEntry = GameRecordContract.GameTypePerformanceEntry

        val solver = gameSaveData.setup.solver
        val evaluator = gameSaveData.setup.evaluator

        val columnTurnCount = getTurnCountColumn(gameSaveData.state)
        var previousColumnTurnCounts: String? = null
        var previousRound = -1
        if (previousOutcome != null) {
            // read the previous turn count and outcome; needed to decrement previous
            // performance record.
            previousRound = previousOutcome.round
            previousColumnTurnCounts = getTurnCountColumn(previousOutcome.outcome.name)
        }

        listOf(gameType, null).forEach {
            // Requires querying the column value and updating it
            // outside the database, or -- if not found -- inserting a new record.
            // We do this in a somewhat inefficient way, but it makes the operations easier.
            val gameTypeName = it?.toString() ?: perfEntry.COLUMN_VALUE_GAME_TYPE_ALL

            val performanceCV = readGameTypePerformance(gameTypeName, gameSaveData.setup.daily, solver.name, evaluator.name, arrayOf(
                BaseColumns._ID,
                perfEntry.COLUMN_NAME_WINNING_TURN_COUNTS,
                perfEntry.COLUMN_NAME_LOSING_TURN_COUNTS,
                perfEntry.COLUMN_NAME_FORFEIT_TURN_COUNTS
            ))

            if (performanceCV == null) {
                Timber.d("no performance record; creating a new one")
                val record = object: PerformanceRecord() { }
                when(gameSaveData.state) {
                    Game.State.WON -> record.winningTurnCounts[gameSaveData.round] = 1
                    Game.State.LOST -> record.losingTurnCounts[gameSaveData.round] = 1
                    else -> record.forfeitTurnCounts[gameSaveData.round] = 1
                }
                dbHelper.writableDatabase.insert(
                    perfEntry.TABLE_NAME,
                    "",
                    putValues(ContentValues(), it, gameSaveData.setup.daily, solver.name, evaluator.name, record)
                )
            } else {
                Timber.d("incrementing existing performance record: $performanceCV")
                Timber.d("adding 1 to ${columnTurnCount}")
                // increment turn count columns
                performanceCV.put(
                    columnTurnCount,
                    IntHistogram.addInString(performanceCV.getAsString(columnTurnCount), gameSaveData.round, 1)
                )

                if (previousColumnTurnCounts != null) {
                    Timber.d("subtracting 1 from ${previousColumnTurnCounts}")
                    performanceCV.put(
                        previousColumnTurnCounts,
                        IntHistogram.addInString(performanceCV.getAsString(previousColumnTurnCounts), previousRound, -1)
                    )
                }
                Timber.d("incremented  existing performance record: $performanceCV")

                // update db record
                val id = performanceCV.getAsLong(BaseColumns._ID)
                performanceCV.remove(BaseColumns._ID)
                dbHelper.writableDatabase.update(
                    perfEntry.TABLE_NAME,
                    performanceCV,
                    "${BaseColumns._ID} = ?",
                    arrayOf("$id")
                )
            }
        }
    }

    override fun hasOutcome(uuid: UUID) =
        readGameOutcome(uuid, arrayOf(GameRecordContract.GameOutcomeEntry.COLUMN_NAME_GAME_UUID)) != null

    override fun getOutcome(uuid: UUID) = GameRecordContract.GameOutcomeEntry.run { readGameOutcome(uuid)?.let {
        GameOutcome(
            uuid = uuid,
            type = GameType.fromString(it.getAsString(COLUMN_NAME_GAME_TYPE)),
            daily = it.getAsBoolean(COLUMN_NAME_DAILY),
            hard = it.getAsBoolean(COLUMN_NAME_HARD),
            solver = GameSetup.Solver.valueOf(it.getAsString(COLUMN_NAME_SOLVER_ROLE)),
            evaluator = GameSetup.Evaluator.valueOf(it.getAsString(COLUMN_NAME_EVALUATOR_ROLE)),
            seed = it.getAsStringOrNull(COLUMN_NAME_SEED),
            outcome = GameOutcome.Outcome.valueOf(it.getAsString(COLUMN_NAME_OUTCOME)),
            round = it.getAsInteger(COLUMN_NAME_CURRENT_ROUND),
            constraints = it.getAsString(COLUMN_NAME_CONSTRAINTS)
                .split("|")
                .filter { it.isNotBlank() }
                .map { c -> Constraint.fromString(c) },
            guess = it.getAsStringOrNull(COLUMN_NAME_GUESS),
            secret = it.getAsStringOrNull(COLUMN_NAME_SECRET),
            rounds = it.getAsInteger(COLUMN_NAME_ROUNDS),
            recordedAt = Date(it.getAsLong(COLUMN_NAME_RECORDED_AT))
        )
    } }

    override fun getTotalPerformance(
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator
    ): TotalPerformanceRecord {
        val type = GameRecordContract.GameTypePerformanceEntry.COLUMN_VALUE_GAME_TYPE_ALL
        return GameRecordContract.GameTypePerformanceEntry.run {
            val columns = arrayOf(
                COLUMN_NAME_WINNING_TURN_COUNTS,
                COLUMN_NAME_LOSING_TURN_COUNTS,
                COLUMN_NAME_FORFEIT_TURN_COUNTS
            )

            // read the strict record
            val record = TotalPerformanceRecord()
            readGameTypePerformance(type, false, solver.name, evaluator.name, columns)?.let {
                record.winningTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
            }

            readGameTypePerformance(type, true, solver.name, evaluator.name, columns)?.let {
                record.winningTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
            }
            record
        }
    }

    override fun getPerformance(outcome: GameOutcome, strict: Boolean): GameTypePerformanceRecord {
        return getPerformance(outcome.type, outcome.daily, outcome.solver, outcome.evaluator, strict)
    }

    override fun getPerformance(setup: GameSetup, strict: Boolean): GameTypePerformanceRecord {
        val gameType = gameSetupManager.getType(setup)
        return getPerformance(gameType, setup.daily, setup.solver, setup.evaluator, strict)
    }

    private fun getPerformance(gameType: GameType, daily: Boolean, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator, strict: Boolean) =
        GameRecordContract.GameTypePerformanceEntry.run {
            val columns = arrayOf(
                COLUMN_NAME_WINNING_TURN_COUNTS,
                COLUMN_NAME_LOSING_TURN_COUNTS,
                COLUMN_NAME_FORFEIT_TURN_COUNTS
            )

            // read the strict record
            val record = GameTypePerformanceRecord(gameType)
            readGameTypePerformance(gameType, daily, solver.name, evaluator.name, columns)?.let {
                Timber.d("winning ${it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS)}")
                Timber.d("losing ${it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS)}")
                Timber.d("forfeit ${it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS)}")
                record.winningTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts.resetFromString(it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
            }

            // combine with alternative if not strict
            if (!strict) {
                readGameTypePerformance(gameType, !daily, solver.name, evaluator.name, columns)?.let {
                    Timber.v("winning ${it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS)}")
                    Timber.v("losing ${it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS)}")
                    Timber.v("forfeit ${it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS)}")
                    record.winningTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                    record.losingTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                    record.forfeitTurnCounts += IntHistogram.fromString(it.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
                }
            }
            record
        }

    override fun getPlayerStreak(outcome: GameOutcome): GameTypePlayerStreak {
        return getPlayerStreak(outcome.type, outcome.solver, outcome.evaluator)
    }

    override fun getPlayerStreak(setup: GameSetup): GameTypePlayerStreak {
        val gameType = gameSetupManager.getType(setup)
        return getPlayerStreak(gameType, setup.solver, setup.evaluator)
    }

    private fun getPlayerStreak(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): GameTypePlayerStreak {
        val streak = GameTypePlayerStreak(gameType)
        if (solver == GameSetup.Solver.PLAYER && evaluator == GameSetup.Evaluator.HONEST) {
            GameRecordContract.PlayerStreakEntry.run {
                readPlayerStreak(gameType)?.let {
                    streak.current = it.getAsInteger(COLUMN_NAME_STREAK)
                    streak.currentDaily = it.getAsInteger(COLUMN_NAME_DAILY_STREAK)
                    streak.best = it.getAsInteger(COLUMN_NAME_BEST_STREAK)
                    streak.bestDaily = it.getAsInteger(COLUMN_NAME_BEST_DAILY_STREAK)
                }
            }
        }
        return streak
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Helper Functions
    //---------------------------------------------------------------------------------------------
    private fun ContentValues.getAsStringOrNull(key: String) = if (containsKey(key)) getAsString(key) else null

    private fun put(values: ContentValues, column: String, value: String?) {
        if (value == null) values.putNull(column) else values.put(column, value)
    }

    private fun put(values: ContentValues, column: String, value: Int?) {
        if (value == null) values.putNull(column) else values.put(column, value)
    }

    private fun put(values: ContentValues, column: String, value: Long?) {
        if (value == null) values.putNull(column) else values.put(column, value)
    }

    private fun put(values: ContentValues, column: String, value: Boolean?) {
        if (value == null) values.putNull(column) else values.put(column, value)
    }

    private fun getTurnCountColumn(state: Game.State) = GameRecordContract.GameTypePerformanceEntry.run { when(state) {
        Game.State.WON -> COLUMN_NAME_WINNING_TURN_COUNTS
        Game.State.LOST -> COLUMN_NAME_LOSING_TURN_COUNTS
        else -> COLUMN_NAME_FORFEIT_TURN_COUNTS
    }}

    private fun getTurnCountColumn(outcome: String) = GameRecordContract.GameTypePerformanceEntry.run { when(outcome) {
        "WON" -> COLUMN_NAME_WINNING_TURN_COUNTS
        "LOST" -> COLUMN_NAME_LOSING_TURN_COUNTS
        else -> COLUMN_NAME_FORFEIT_TURN_COUNTS
    }}

    private fun putValues(
        values: ContentValues,
        seed: String?,
        setup: GameSetup,
        game: Game,
        secret: String?,
        columns: Array<String> = GameRecordContract.GameOutcomeEntry.COLUMNS
    ): ContentValues = GameRecordContract.GameOutcomeEntry.run {
        for (column in columns) {
            when(column) {
                COLUMN_NAME_GAME_UUID -> put(values, column, game.uuid.toString())
                COLUMN_NAME_GAME_TYPE -> put(values, column, gameSetupManager.getType(setup).toString())
                COLUMN_NAME_SOLVER_ROLE -> put(values, column, setup.solver.name)
                COLUMN_NAME_EVALUATOR_ROLE -> put(values, column, setup.evaluator.name)
                COLUMN_NAME_SEED -> put(values, column, seed)
                COLUMN_NAME_OUTCOME -> put(values, column, when(game.state) {
                    Game.State.WON -> "WON"
                    Game.State.LOST -> "LOST"
                    else -> "FORFEIT"
                })
                COLUMN_NAME_CURRENT_ROUND -> put(values, column, game.round)
                COLUMN_NAME_CONSTRAINTS -> put(values, column, game.constraints.map { it.toString() }.joinToString("|"))
                COLUMN_NAME_GUESS -> put(values, column, game.currentGuess)
                COLUMN_NAME_SECRET -> put(values, column, secret)
                COLUMN_NAME_ROUNDS -> put(values, column, game.settings.rounds)
                COLUMN_NAME_RECORDED_AT -> put(values, column, Date().time)
            }
        }
        values
    }

    private fun putValues(
        values: ContentValues,
        gameSaveData: GameSaveData,
        secret: String?,
        columns: Array<String> = GameRecordContract.GameOutcomeEntry.COLUMNS
    ): ContentValues = GameRecordContract.GameOutcomeEntry.run {
        for (column in columns) {
            when(column) {
                COLUMN_NAME_GAME_UUID -> put(values, column, gameSaveData.uuid.toString())
                COLUMN_NAME_GAME_TYPE -> put(values, column, gameSetupManager.getType(gameSaveData.setup).toString())
                COLUMN_NAME_DAILY -> put(values, column, gameSaveData.setup.daily)
                COLUMN_NAME_HARD -> put(values, column, gameSetupManager.isHard(gameSaveData.setup))
                COLUMN_NAME_SOLVER_ROLE -> put(values, column, gameSaveData.setup.solver.name)
                COLUMN_NAME_EVALUATOR_ROLE -> put(values, column, gameSaveData.setup.evaluator.name)
                COLUMN_NAME_SEED -> put(values, column, gameSaveData.seed)
                COLUMN_NAME_OUTCOME -> put(values, column, when(gameSaveData.state) {
                    Game.State.WON -> "WON"
                    Game.State.LOST -> "LOST"
                    else -> "FORFEIT"
                })
                COLUMN_NAME_CURRENT_ROUND -> put(values, column, gameSaveData.round)
                COLUMN_NAME_CONSTRAINTS -> put(values, column, gameSaveData.constraints.map { it.toString() }.joinToString("|"))
                COLUMN_NAME_GUESS -> put(values, column, gameSaveData.currentGuess)
                COLUMN_NAME_SECRET -> put(values, column, secret)
                COLUMN_NAME_ROUNDS -> put(values, column, gameSaveData.settings.rounds)
                COLUMN_NAME_RECORDED_AT -> put(values, column, Date().time)
            }
        }
        values
    }

    private fun putValues(
        values: ContentValues,
        gameType: GameType?,
        daily: Boolean,
        solver: String,
        evaluator: String,
        performance: PerformanceRecord,
        columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS
    ): ContentValues = GameRecordContract.GameTypePerformanceEntry.run {
        for (column in columns) {
            when(column) {
                COLUMN_NAME_GAME_TYPE -> put(values, column, gameType?.toString() ?: COLUMN_VALUE_GAME_TYPE_ALL)
                COLUMN_NAME_DAILY -> put(values, column, daily)
                COLUMN_NAME_SOLVER_ROLE -> put(values, column, solver)
                COLUMN_NAME_EVALUATOR_ROLE -> put(values, column, evaluator)
                COLUMN_NAME_WINNING_TURN_COUNTS -> put(values, column, performance.winningTurnCounts.toString())
                COLUMN_NAME_LOSING_TURN_COUNTS -> put(values, column, performance.losingTurnCounts.toString())
                COLUMN_NAME_FORFEIT_TURN_COUNTS -> put(values, column, performance.forfeitTurnCounts.toString())
            }
        }
        values
    }

    private fun readGameOutcome(uuid: UUID, columns: Array<String> = GameRecordManagerImpl.GameRecordContract.GameOutcomeEntry.COLUMNS): ContentValues? {
        val entry = GameRecordContract.GameOutcomeEntry
        synchronized(this) {
            return dbHelper.readableDatabase.query(
                entry.TABLE_NAME,
                columns,
                "${entry.COLUMN_NAME_GAME_UUID} = ?",
                arrayOf(uuid.toString()),
                "", "", "", "1"
            ).use {
                if (!it.moveToFirst()) null else {
                    val values = ContentValues()
                    for (i in columns.indices) {
                        when (val column = columns[i]) {
                            entry.COLUMN_NAME_ROUNDS -> put(values, column, it.getIntOrNull(i))
                            entry.COLUMN_NAME_RECORDED_AT -> put(values, column, it.getLongOrNull(i))
                            else -> put(values, column, it.getStringOrNull(i))
                        }
                    }
                    values
                }
            }
        }
    }

    private fun readGameTypePerformance(
        gameType: GameType,
        daily: Boolean,
        solver: String,
        evaluator: String,
        columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS
    ) = readGameTypePerformance(gameType.toString(), daily, solver, evaluator, columns)

    private fun readGameTypePerformance(
        gameType: String,
        daily: Boolean,
        solver: String,
        evaluator: String,
        columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS
    ): ContentValues? {
        val entry = GameRecordContract.GameTypePerformanceEntry

        val select = mutableListOf(
            "${entry.COLUMN_NAME_GAME_TYPE} = ?",
            "${entry.COLUMN_NAME_DAILY} = ?",
            "${entry.COLUMN_NAME_SOLVER_ROLE} = ?",
            "${entry.COLUMN_NAME_EVALUATOR_ROLE} = ?"
        )
        val selectArgs = mutableListOf(
            gameType,
            if (daily) "1" else "0",
            solver,
            evaluator
        )

        Timber.d("retrieve ${select} ${selectArgs}")

        synchronized(this) {
            return dbHelper.readableDatabase.query(
                entry.TABLE_NAME,
                columns,
                select.joinToString(" AND "),
                selectArgs.toTypedArray(),
                "", "", "", "1"
            ).use {
                if (!it.moveToFirst()) null else {
                    val values = ContentValues()
                    for (i in columns.indices) {
                        when (val column = columns[i]) {
                            BaseColumns._ID -> put(values, column, it.getLongOrNull(i))
                            else -> put(values, column, it.getStringOrNull(i))
                        }
                    }
                    Timber.d("retrieved ${values}")
                    values
                }
            }
        }
    }

    private fun readPlayerStreak(
        gameType: GameType,
        columns: Array<String> = GameRecordContract.PlayerStreakEntry.COLUMNS
    ): ContentValues? {
        val entry = GameRecordContract.PlayerStreakEntry
        synchronized(this) {
            return dbHelper.readableDatabase.query(
                entry.TABLE_NAME,
                columns,
                "${entry.COLUMN_NAME_GAME_TYPE} = ?",
                arrayOf(gameType.toString()),
                "", "", "", "1"
            ).use {
                if (!it.moveToFirst()) null else {
                    val values = ContentValues()
                    for (i in columns.indices) {
                        when (val column = columns[i]) {
                            BaseColumns._ID -> put(values, column, it.getLongOrNull(i))
                            else -> put(values, column, it.getInt(i))
                        }
                    }
                    values
                }
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}