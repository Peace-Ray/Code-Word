package com.peaceray.codeword.data.source.impl

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.GameTypePerformanceRecord
import com.peaceray.codeword.data.model.record.GameTypePlayerStreak
import com.peaceray.codeword.data.model.record.PerformanceRecord
import com.peaceray.codeword.data.model.record.PlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import com.peaceray.codeword.data.source.CodeWordDb
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.utils.histogram.IntHistogram
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CodeWordDbImpl @Inject constructor(
    @ForApplication val context: Context,
): CodeWordDb {

    //region Properties and Caches
    //---------------------------------------------------------------------------------------------
    private val dbHelper: CodeWordDbHelper by lazy { CodeWordDbHelper(context) }
    // TODO cache row IDs for unique identifiers, e.g. UUID -> GameOutcomeEntry._ID?
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Properties and Caches
    //---------------------------------------------------------------------------------------------

    override fun putOutcome(outcome: GameOutcome) {
        GameRecordContract.GameOutcomeEntry.run {
            val cv = toContentValues(outcome)
            val db = dbHelper.writableDatabase
            var rowId = -1L
            try {
                rowId = db.insertWithOnConflict(TABLE_NAME, "", cv, SQLiteDatabase.CONFLICT_ABORT)
            } catch (err: SQLiteConstraintException) {
                Timber.d(err, "outcome already in the database")
            }

            if (rowId < 0) {
                db.update(TABLE_NAME, cv, "$COLUMN_NAME_GAME_UUID = ?", arrayOf(outcome.uuid.toString()))
            }
        }
    }

    override fun hasOutcome(uuid: UUID): Boolean {
        return GameRecordContract.GameOutcomeEntry.run {
            readGameOutcomes(uuid, arrayOf(COLUMN_NAME_GAME_UUID)).isNotEmpty()
        }
    }

    override fun getOutcomeOutcome(uuid: UUID): Pair<GameOutcome.Outcome, Int>? {
        return GameRecordContract.GameOutcomeEntry.run {
            val cv = readGameOutcomes(uuid, arrayOf(
                COLUMN_NAME_OUTCOME,
                COLUMN_NAME_CURRENT_ROUND
            )).firstOrNull()
            if (cv == null) null else Pair(
                GameOutcome.Outcome.valueOf(cv.getAsString(COLUMN_NAME_OUTCOME)),
                cv.getAsInteger(COLUMN_NAME_CURRENT_ROUND)
            )
        }
    }

    override fun getOutcome(uuid: UUID): GameOutcome? {
        val cv = readGameOutcomes(uuid).firstOrNull()
        return if (cv == null) null else GameRecordContract.GameOutcomeEntry.fromContentValues(cv)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Performance Records.
    //---------------------------------------------------------------------------------------------
    override fun updatePerformanceRecords(
        gameType: GameType,
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean,
        add: List<Pair<GameOutcome.Outcome, Int>>,
        remove: List<Pair<GameOutcome.Outcome, Int>>
    ) {
        GameRecordContract.GameTypePerformanceEntry.run {
            val getTurnCountColumn: (GameOutcome.Outcome) -> String = { outcome ->
                when (outcome) {
                    GameOutcome.Outcome.WON -> COLUMN_NAME_WINNING_TURN_COUNTS
                    GameOutcome.Outcome.LOST -> COLUMN_NAME_LOSING_TURN_COUNTS
                    GameOutcome.Outcome.FORFEIT,
                    GameOutcome.Outcome.LOADING -> COLUMN_NAME_FORFEIT_TURN_COUNTS
                }
            }

            // update records for this game type and for the ALL types row
            val selections = listOf(gameType.toString(), COLUMN_VALUE_GAME_TYPE_ALL).map {
                mapOf(
                    Pair(COLUMN_NAME_GAME_TYPE, it),
                    Pair(COLUMN_NAME_SOLVER_ROLE, solver.name),
                    Pair(COLUMN_NAME_EVALUATOR_ROLE, evaluator.name),
                    Pair(COLUMN_NAME_DAILY, if (daily) "1" else "0")
                )
            }
            selections.forEach { selection ->
                val db = dbHelper.writableDatabase
                val cvIn = readPerformanceRecords(selection).firstOrNull()
                if (cvIn == null) {
                    Timber.d("no performance record; creating a new one")
                    val isTotal = selection[COLUMN_NAME_GAME_TYPE] == COLUMN_VALUE_GAME_TYPE_ALL
                    val record = if (isTotal) TotalPerformanceRecord(daily) else {
                        GameTypePerformanceRecord(gameType, daily)
                    }
                    add.forEach { when (it.first) {
                        GameOutcome.Outcome.WON -> record.winningTurnCounts[it.second] += 1
                        GameOutcome.Outcome.LOST -> record.losingTurnCounts[it.second] += 1
                        GameOutcome.Outcome.FORFEIT,
                        GameOutcome.Outcome.LOADING -> record.forfeitTurnCounts[it.second] += 1
                    } }
                    remove.forEach { when (it.first) {
                        GameOutcome.Outcome.WON -> record.winningTurnCounts[it.second] -= 1
                        GameOutcome.Outcome.LOST -> record.losingTurnCounts[it.second] -= 1
                        GameOutcome.Outcome.FORFEIT,
                        GameOutcome.Outcome.LOADING -> record.forfeitTurnCounts[it.second] -= 1
                    } }
                    val cvOut = toContentValues(record)
                    cvOut.put(COLUMN_NAME_SOLVER_ROLE, solver.name)
                    cvOut.put(COLUMN_NAME_EVALUATOR_ROLE, evaluator.name)
                    db.insert(TABLE_NAME, "", cvOut)
                } else {
                    Timber.d("updating existing performance record")
                    val cvOut = ContentValues()
                    add.forEach { entry ->
                        val column = getTurnCountColumn(entry.first)
                        val prev = cvOut.getAsStringOrNull(column) ?: cvIn.getAsString(column)
                        cvOut.put(column, IntHistogram.addInString(prev, entry.second, 1))
                    }
                    remove.forEach { entry ->
                        val column = getTurnCountColumn(entry.first)
                        val prev = cvOut.getAsStringOrNull(column) ?: cvIn.getAsString(column)
                        cvOut.put(column, IntHistogram.addInString(prev, entry.second, -1))
                    }
                    val id = cvIn.getAsLong(BaseColumns._ID)
                    db.update(TABLE_NAME, cvOut, "${BaseColumns._ID} = ?", arrayOf("$id"))
                }
            }
        }
    }

    override fun addToPerformanceRecords(
        gameType: GameType,
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean,
        outcome: GameOutcome.Outcome,
        round: Int
    ) = updatePerformanceRecords(gameType, solver, evaluator, daily, add = listOf(Pair(outcome, round)))

    override fun removeFromPerformanceRecords(
        gameType: GameType,
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean,
        outcome: GameOutcome.Outcome,
        round: Int
    ) = updatePerformanceRecords(gameType, solver, evaluator, daily, remove = listOf(Pair(outcome, round)))

    override fun getTotalPerformanceRecord(
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator
    ): TotalPerformanceRecord {
        val cvs = readPerformanceRecords(solver, evaluator, null)
        return GameRecordContract.GameTypePerformanceEntry.run {
            val record = TotalPerformanceRecord(null)
            cvs.forEach { cv ->
                record.winningTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
            }
            record
        }
    }

    override fun getGameTypePerformanceRecord(
        gameType: GameType,
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean?
    ): GameTypePerformanceRecord {
        val cvs = readPerformanceRecords(gameType, solver, evaluator, daily)
        return GameRecordContract.GameTypePerformanceEntry.run {
            val record = GameTypePerformanceRecord(gameType, daily)
            cvs.forEach { cv ->
                record.winningTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts += IntHistogram.fromString(cv.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
            }
            record
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Player Streaks
    //---------------------------------------------------------------------------------------------
    override fun updatePlayerStreak(
        gameType: GameType,
        daily: Boolean,
        outcome: GameOutcome.Outcome
    ) {
        GameRecordContract.PlayerStreakEntry.run {
            // create if not extant
            dbHelper.writableDatabase.insertWithOnConflict(
                TABLE_NAME,
                "",
                toContentValues(GameTypePlayerStreak(gameType)),
                SQLiteDatabase.CONFLICT_IGNORE
            )

            // create update SQL statement
            val updates = mutableListOf<String>()
            if (outcome == GameOutcome.Outcome.WON) {
                updates.add("$COLUMN_NAME_BEST_STREAK = max($COLUMN_NAME_BEST_STREAK, $COLUMN_NAME_STREAK + 1)")
                updates.add("$COLUMN_NAME_STREAK = $COLUMN_NAME_STREAK + 1")
                if (daily) {
                    updates.add("$COLUMN_NAME_BEST_DAILY_STREAK = max($COLUMN_NAME_BEST_DAILY_STREAK, $COLUMN_NAME_DAILY_STREAK + 1)")
                    updates.add("$COLUMN_NAME_DAILY_STREAK = $COLUMN_NAME_DAILY_STREAK + 1")
                }
            } else {
                updates.add("$COLUMN_NAME_STREAK = 0")
                if (daily) {
                    updates.add("$COLUMN_NAME_DAILY_STREAK = 0")
                }
            }

            val sql = "UPDATE $TABLE_NAME SET ${updates.joinToString(",")} WHERE $COLUMN_NAME_GAME_TYPE = ?;"

            // run statement
            val stmt = dbHelper.writableDatabase.compileStatement(sql)
            stmt.bindString(1, gameType.toString())
            val rows = stmt.executeUpdateDelete()
            if (rows != 1) Timber.e("Failed to create or update $TABLE_NAME row")
        }
    }

    override fun getPlayerStreak(
        gameType: GameType
    ): GameTypePlayerStreak {
        return GameRecordContract.PlayerStreakEntry.run {
            val cv = readPlayerStreaks(gameType).firstOrNull()
            if (cv == null) GameTypePlayerStreak(gameType) else fromContentValues(cv)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Database Helper
    //---------------------------------------------------------------------------------------------
    class CodeWordDbHelper(context: Context): SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase?) {
            db?.execSQL("${GameRecordContract.GameOutcomeEntry.SQL_CREATE_TABLE};")
            db?.execSQL("${GameRecordContract.GameTypePerformanceEntry.SQL_CREATE_TABLE};")
            db?.execSQL("${GameRecordContract.PlayerStreakEntry.SQL_CREATE_TABLE};")
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                // at this version, GameTypes use an expanded string representation that
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

            if (oldVersion < 3) {
                // at this first, a "hinting_round" column was added to game_outcome, indicating
                // when the player activated hints with "-1" meaning no hints activated.
                GameRecordContract.GameOutcomeEntry.run {
                    // wanted non-null, but with no default value. This is impossible to do in
                    // SQLite, at least safely, since you can't ALTER COLUMN to add a "non-null"
                    // constraint or remove a default value.
                    db?.execSQL("ALTER TABLE $TABLE_NAME ADD COLUMN $COLUMN_NAME_HINTING_ROUND INTEGER DEFAULT -1")
                }
            }
        }

        companion object {
            /**
             * Version 1: Original
             * Version 2: Expanded GameType string representation to explicitly note Feedback Type
             * Version 3: Adds "hinting_round" to Outcome, indicating when hinting was first enabled (-1 for never).
             */
            const val DATABASE_VERSION = 3
            const val DATABASE_NAME = "game_record.db"
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Database Reads
    //---------------------------------------------------------------------------------------------

    private fun readGameOutcomes(uuid: UUID, columns: Array<String> = GameRecordContract.GameOutcomeEntry.COLUMNS): List<ContentValues> {
        val selection = mapOf(Pair(GameRecordContract.GameOutcomeEntry.COLUMN_NAME_GAME_UUID, uuid))
        return readGameOutcomes(selection, columns)
    }

    private fun readGameOutcomes(selection: Map<String, Any>, columns: Array<String> = GameRecordContract.GameOutcomeEntry.COLUMNS): List<ContentValues> {
        return GameRecordContract.GameOutcomeEntry.run {
            if (selection.keys.any { it !in COLUMNS }) throw IllegalArgumentException("Unsupported selection column")
            val selectionEntries = selection.entries.toList()
            dbHelper.readableDatabase.query(
                TABLE_NAME,
                columns,
                selectionEntries.joinToString(" AND ") { "${it.key} = ?" },
                selectionEntries.map { it.value.toString() }.toTypedArray(),
                "", "", ""
            ).use {
                if (!it.moveToFirst()) emptyList() else {
                    val entries = mutableListOf<ContentValues>()
                    do {
                        val cv = ContentValues()
                        for (i in columns.indices) {
                            when (val column = columns[i]) {
                                COLUMN_NAME_ROUNDS -> cv.putOrNull(column, it.getIntOrNull(i))
                                COLUMN_NAME_RECORDED_AT -> cv.putOrNull(column, it.getLongOrNull(i))
                                else -> cv.putOrNull(column, it.getStringOrNull(i))
                            }
                        }
                        entries.add(cv)
                        it.moveToNext()
                    } while (!it.isAfterLast)
                    entries.toList()
                }
            }
        }
    }

    private fun readPerformanceRecords(
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean?,
        columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS
    ): List<ContentValues> {
        val selection = GameRecordContract.GameTypePerformanceEntry.run {
            val map = mutableMapOf(
                Pair(COLUMN_NAME_GAME_TYPE, COLUMN_VALUE_GAME_TYPE_ALL),
                Pair(COLUMN_NAME_SOLVER_ROLE, solver.name),
                Pair(COLUMN_NAME_EVALUATOR_ROLE, evaluator.name)
            )
            if (daily != null) map[COLUMN_NAME_DAILY] = if (daily) "1" else "0"
            map.toMap()
        }
        return readPerformanceRecords(selection, columns)
    }

    private fun readPerformanceRecords(
        gameType: GameType,
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator,
        daily: Boolean?,
        columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS
    ): List<ContentValues> {
        val selection = GameRecordContract.GameTypePerformanceEntry.run {
            val map = mutableMapOf(
                Pair(COLUMN_NAME_GAME_TYPE, gameType.toString()),
                Pair(COLUMN_NAME_SOLVER_ROLE, solver.name),
                Pair(COLUMN_NAME_EVALUATOR_ROLE, evaluator.name)
            )
            if (daily != null) map[COLUMN_NAME_DAILY] = if (daily) "1" else "0"
            map.toMap()
        }
        return readPerformanceRecords(selection, columns)
    }

    private fun readPerformanceRecords(selection: Map<String, Any>, columns: Array<String> = GameRecordContract.GameTypePerformanceEntry.COLUMNS): List<ContentValues> {
        return GameRecordContract.GameTypePerformanceEntry.run {
            if (selection.keys.any { it !in COLUMNS }) throw IllegalArgumentException("Unsupported selection column")
            val selectionEntries = selection.entries.toList()
            dbHelper.readableDatabase.query(
                TABLE_NAME,
                columns,
                selectionEntries.joinToString(" AND ") { "${it.key} = ?" },
                selectionEntries.map { it.value.toString() }.toTypedArray(),
                "", "", ""
            ).use {
                if (!it.moveToFirst()) emptyList() else {
                    val entries = mutableListOf<ContentValues>()
                    do {
                        val cv = ContentValues()
                        for (i in columns.indices) {
                            cv.putOrNull(columns[i], it.getStringOrNull(i))
                        }
                        entries.add(cv)
                        it.moveToNext()
                    } while (!it.isAfterLast)
                    entries.toList()
                }
            }
        }
    }

    private fun readPlayerStreaks(gameType: GameType, columns: Array<String> = GameRecordContract.PlayerStreakEntry.COLUMNS): List<ContentValues> {
        return GameRecordContract.PlayerStreakEntry.run {
            readPlayerStreaks(mapOf(
                Pair(COLUMN_NAME_GAME_TYPE, gameType.toString())
            ), columns)
        }
    }

    private fun readPlayerStreaks(selection: Map<String, Any>, columns: Array<String> = GameRecordContract.PlayerStreakEntry.COLUMNS): List<ContentValues> {
        return GameRecordContract.PlayerStreakEntry.run {
            if (selection.keys.any { it !in COLUMNS }) throw IllegalArgumentException("Unsupported selection column")
            val selectionEntries = selection.entries.toList()
            dbHelper.readableDatabase.query(
                TABLE_NAME,
                columns,
                selectionEntries.joinToString( " AND ") { "${it.key} = ?" },
                selectionEntries.map { it.value.toString() }.toTypedArray(),
                "", "", ""
            ).use {
                if (!it.moveToFirst()) emptyList() else {
                    val entries = mutableListOf<ContentValues>()
                    do {
                        val cv = ContentValues()
                        for (i in columns.indices) {
                            when (val column = columns[i]) {
                                COLUMN_NAME_STREAK,
                                COLUMN_NAME_DAILY_STREAK,
                                COLUMN_NAME_BEST_STREAK,
                                COLUMN_NAME_BEST_DAILY_STREAK -> cv.putOrNull(column, it.getIntOrNull(i))
                                else -> cv.putOrNull(column, it.getStringOrNull(i))
                            }
                            cv.putOrNull(columns[i], it.getStringOrNull(i))
                        }
                        entries.add(cv)
                        it.moveToNext()
                    } while (!it.isAfterLast)
                    entries.toList()
                }
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Database Contracts
    //---------------------------------------------------------------------------------------------
    object GameRecordContract {
        // Base Class
        interface Entry<T> {
            val TABLE_NAME: String
            val COLUMNS: Array<String>
            val SQL_CREATE_TABLE: String

            fun toContentValues(t: T, columns: Array<String> = COLUMNS): ContentValues
            fun fromContentValues(cv: ContentValues): T
        }

        // Game Outcome Record Table
        object GameOutcomeEntry: Entry<GameOutcome> {
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
            const val COLUMN_NAME_HINTING_ROUND = "hinting_round"
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
                COLUMN_NAME_HINTING_ROUND,
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
                        "$COLUMN_NAME_HINTING_ROUND INTEGER DEFAULT -1," +  // prefer no default but SQLite can't alter columns
                        "$COLUMN_NAME_CONSTRAINTS TEXT NOT NULL," +
                        "$COLUMN_NAME_GUESS TEXT," +
                        "$COLUMN_NAME_SECRET TEXT," +
                        "$COLUMN_NAME_ROUNDS INTEGER NOT NULL," +
                        "$COLUMN_NAME_RECORDED_AT BIGINT NOT NULL" +
                        ")"

            override fun toContentValues(t: GameOutcome, columns: Array<String>): ContentValues {
                val cv = ContentValues()
                for (column in columns) {
                    when (column) {
                        COLUMN_NAME_GAME_UUID -> cv.putOrNull(column, t.uuid.toString())
                        COLUMN_NAME_GAME_TYPE -> cv.putOrNull(column, t.type.toString())
                        COLUMN_NAME_DAILY -> cv.putOrNull(column, t.daily)
                        COLUMN_NAME_HARD -> cv.putOrNull(column, t.hard)
                        COLUMN_NAME_SOLVER_ROLE -> cv.putOrNull(column, t.solver.name)
                        COLUMN_NAME_EVALUATOR_ROLE -> cv.putOrNull(column, t.evaluator.name)
                        COLUMN_NAME_SEED -> cv.putOrNull(column, t.seed)
                        COLUMN_NAME_OUTCOME -> cv.putOrNull(column, when(t.outcome) {
                            GameOutcome.Outcome.WON -> "WON"
                            GameOutcome.Outcome.LOST -> "LOST"
                            GameOutcome.Outcome.FORFEIT,
                            GameOutcome.Outcome.LOADING -> "FORFEIT"
                        })
                        COLUMN_NAME_CURRENT_ROUND -> cv.putOrNull(column, t.round)
                        COLUMN_NAME_HINTING_ROUND -> cv.putOrNull(column, t.hintingSinceRound)
                        COLUMN_NAME_CONSTRAINTS -> cv.putOrNull(column, t.constraints.joinToString("|") { it.toString() })
                        COLUMN_NAME_GUESS -> cv.putOrNull(column, t.guess)
                        COLUMN_NAME_SECRET -> cv.putOrNull(column, t.secret)
                        COLUMN_NAME_ROUNDS -> cv.putOrNull(column, t.rounds)
                        COLUMN_NAME_RECORDED_AT -> cv.putOrNull(column, Date().time)
                    }
                }
                return cv
            }

            override fun fromContentValues(cv: ContentValues) = GameOutcome(
                uuid = UUID.fromString(cv.getAsString(COLUMN_NAME_GAME_UUID)),
                type = GameType.fromString(cv.getAsString(COLUMN_NAME_GAME_TYPE)),
                daily = cv.getAsBoolean(COLUMN_NAME_DAILY),
                hard = cv.getAsBoolean(COLUMN_NAME_HARD),
                solver = GameSetup.Solver.valueOf(cv.getAsString(COLUMN_NAME_SOLVER_ROLE)),
                evaluator = GameSetup.Evaluator.valueOf(cv.getAsString(
                    COLUMN_NAME_EVALUATOR_ROLE
                )),
                seed = cv.getAsStringOrNull(COLUMN_NAME_SEED),
                outcome = GameOutcome.Outcome.valueOf(cv.getAsString(COLUMN_NAME_OUTCOME)),
                round = cv.getAsInteger(COLUMN_NAME_CURRENT_ROUND),
                hintingSinceRound = cv.getAsInteger(COLUMN_NAME_HINTING_ROUND),
                constraints = cv.getAsString(COLUMN_NAME_CONSTRAINTS)
                    .split("|")
                    .filter { it.isNotBlank() }
                    .map { c -> Constraint.fromString(c) },
                guess = cv.getAsStringOrNull(COLUMN_NAME_GUESS),
                secret = cv.getAsStringOrNull(COLUMN_NAME_SECRET),
                rounds = cv.getAsInteger(COLUMN_NAME_ROUNDS),
                recordedAt = Date(cv.getAsLong(COLUMN_NAME_RECORDED_AT))
            )
        }

        // Game Type Performance Record Table
        object GameTypePerformanceEntry: Entry<PerformanceRecord>, BaseColumns {
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

            override fun toContentValues(
                t: PerformanceRecord,
                columns: Array<String>
            ): ContentValues {
                val cv = ContentValues()
                for (column in columns) {
                    when (column) {
                        COLUMN_NAME_GAME_TYPE -> when (t) {
                            is GameTypePerformanceRecord -> cv.put(column, t.type.toString())
                            is TotalPerformanceRecord -> cv.put(column, COLUMN_NAME_DAILY)
                        }
                        COLUMN_NAME_DAILY -> when (t) {
                            is GameTypePerformanceRecord -> cv.put(column, t.daily)
                            is TotalPerformanceRecord -> cv.put(column, t.daily)
                        }
                        COLUMN_NAME_WINNING_TURN_COUNTS -> cv.put(column, t.winningTurnCounts.toString())
                        COLUMN_NAME_LOSING_TURN_COUNTS -> cv.put(column, t.losingTurnCounts.toString())
                        COLUMN_NAME_FORFEIT_TURN_COUNTS -> cv.put(column, t.forfeitTurnCounts.toString())
                    }
                }
                return cv
            }

            override fun fromContentValues(cv: ContentValues): PerformanceRecord {
                if (cv.getAsString(COLUMN_NAME_SOLVER_ROLE) != GameSetup.Solver.PLAYER.name) {
                    throw IllegalArgumentException("No support for Solver != PLAYER")
                } else if (cv.getAsString(COLUMN_NAME_EVALUATOR_ROLE) != GameSetup.Evaluator.HONEST.name) {
                    throw IllegalArgumentException("No support for GameSetup.Evaluator != HONEST")
                }

                val type = cv.getAsString(COLUMN_NAME_GAME_TYPE)
                val daily = cv.getAsBoolean(COLUMN_NAME_DAILY)
                val record = if (type == COLUMN_VALUE_GAME_TYPE_ALL) TotalPerformanceRecord(daily) else {
                    GameTypePerformanceRecord(GameType.fromString(type), daily)
                }

                record.winningTurnCounts.resetFromString(cv.getAsString(COLUMN_NAME_WINNING_TURN_COUNTS))
                record.losingTurnCounts.resetFromString(cv.getAsString(COLUMN_NAME_LOSING_TURN_COUNTS))
                record.forfeitTurnCounts.resetFromString(cv.getAsString(COLUMN_NAME_FORFEIT_TURN_COUNTS))
                return record
            }
        }

        // Player Streak
        object PlayerStreakEntry: Entry<PlayerStreak>, BaseColumns {
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

            override fun toContentValues(t: PlayerStreak, columns: Array<String>): ContentValues {
                val cv = ContentValues()
                for (column in columns) {
                    when (column) {
                        COLUMN_NAME_GAME_TYPE -> when (t) {
                            is GameTypePlayerStreak -> cv.put(column, t.type.toString())
                        }
                        COLUMN_NAME_STREAK -> cv.put(column, t.current)
                        COLUMN_NAME_BEST_STREAK -> cv.put(column, t.best)
                        COLUMN_NAME_DAILY_STREAK -> cv.put(column, t.currentDaily)
                        COLUMN_NAME_BEST_DAILY_STREAK -> cv.put(column, t.bestDaily)
                    }
                }
                return cv
            }

            override fun fromContentValues(cv: ContentValues): GameTypePlayerStreak {
                val streak = GameTypePlayerStreak(GameType.fromString(cv.getAsString(COLUMN_NAME_GAME_TYPE)))
                streak.current = cv.getAsInteger(COLUMN_NAME_STREAK)
                streak.best = cv.getAsInteger(COLUMN_NAME_BEST_STREAK)
                streak.currentDaily = cv.getAsInteger(COLUMN_NAME_DAILY_STREAK)
                streak.bestDaily = cv.getAsInteger(COLUMN_NAME_BEST_DAILY_STREAK)
                return streak
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}

//region ContentValues Helper Functions
//---------------------------------------------------------------------------------------------
private fun ContentValues.getAsStringOrNull(key: String) = if (containsKey(key)) getAsString(key) else null

private fun ContentValues.putOrNull(column: String, value: String?) {
    if (value == null) putNull(column) else put(column, value)
}

private fun ContentValues.putOrNull(column: String, value: Int?) {
    if (value == null) putNull(column) else put(column, value)
}

private fun ContentValues.putOrNull(column: String, value: Long?) {
    if (value == null) putNull(column) else put(column, value)
}

private fun ContentValues.putOrNull(column: String, value: Boolean?) {
    if (value == null) putNull(column) else put(column, value)
}
//---------------------------------------------------------------------------------------------
//endregion