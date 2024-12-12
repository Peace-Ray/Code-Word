package com.peaceray.codeword.data

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.GameTypePerformanceRecord
import com.peaceray.codeword.data.model.record.GameTypePlayerStreak
import com.peaceray.codeword.data.model.record.PerformanceRecord
import com.peaceray.codeword.data.model.record.PlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import com.peaceray.codeword.data.source.CodeWordDb
import com.peaceray.codeword.data.source.impl.CodeWordDbImpl
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import java.util.Date
import java.util.UUID
import kotlin.math.exp
import kotlin.math.max

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TestCodeWordDb {

    //region Tools
    //---------------------------------------------------------------------------------------------

    private fun createGameType(
        language: CodeLanguage = CodeLanguage.ENGLISH,
        length: Int = if (language == CodeLanguage.ENGLISH) 5 else 4,
        characters: Int = if (language == CodeLanguage.ENGLISH) 26 else 6,
        characterOccurrences: Int = length,
        feedback: ConstraintPolicy = if (language == CodeLanguage.ENGLISH) ConstraintPolicy.ALL else ConstraintPolicy.AGGREGATED
    ) = GameType(language, length, characters, characterOccurrences, feedback)

    private fun createGameOutcome(
        uuid: UUID = UUID.randomUUID(),
        type: GameType = createGameType(),
        daily: Boolean = false,
        hard: Boolean = false,
        solver: GameSetup.Solver = GameSetup.Solver.PLAYER,
        evaluator: GameSetup.Evaluator = GameSetup.Evaluator.HONEST,
        seed: String? = null,
        outcome: GameOutcome.Outcome = GameOutcome.Outcome.WON,
        constraints: List<Constraint> = listOf(),   // TODO real constraints?
        rounds: Int = max(6, constraints.size),
        round: Int = if (constraints.isNotEmpty()) constraints.size else max(0, rounds - 2),
        hintingSinceRound: Int = -1,
        guess: String? = null,
        secret: String? = null,
        recordedAt: Date = Date()
    ) = GameOutcome(
        uuid, type, daily, hard, solver, evaluator, seed, outcome, round, hintingSinceRound,
        constraints, guess, secret, rounds, recordedAt
    )

    private fun createPerformanceRecord(
        type: GameType? = null,
        daily: Boolean? = null,
        results: List<Triple<GameOutcome.Outcome, Int, Int>> = listOf()
    ): PerformanceRecord {
        val record = if (type == null) {
            TotalPerformanceRecord(daily)
        } else {
            GameTypePerformanceRecord(type, daily)
        }

        results.forEach { when(it.first) {
            GameOutcome.Outcome.WON -> record.winningTurnCounts[it.second] += it.third
            GameOutcome.Outcome.LOST -> record.losingTurnCounts[it.second] += it.third
            GameOutcome.Outcome.FORFEIT -> record.forfeitTurnCounts[it.second] += it.third
            GameOutcome.Outcome.LOADING -> record.forfeitTurnCounts[it.second] += it.third
        } }

        return record
    }

    private fun createPlayerStreak(
        type: GameType,
        current: Int = 0,
        best: Int = current,
        currentDaily: Int = 0,
        bestDaily: Int = currentDaily
    ): GameTypePlayerStreak {
        val streak = GameTypePlayerStreak(type)
        streak.current = current
        streak.best = best
        streak.currentDaily = currentDaily
        streak.bestDaily = bestDaily
        return streak
    }

    private fun assertEqualsGameOutcome(expected: GameOutcome, actual: GameOutcome?) {
        if (actual == null) {
            assertEquals(expected, null)
        } else {
            try {
                // all fields except "recordedAt" which is determined by moment inserted into DB.
                val fieldsExpected = expected.run { listOf<Any?>(
                    uuid, type, daily, hard, solver, evaluator, seed, outcome, round, hintingSinceRound,
                    constraints, guess, secret, rounds
                ) }
                val fieldsActual = actual.run { listOf<Any?>(
                    uuid, type, daily, hard, solver, evaluator, seed, outcome, round, hintingSinceRound,
                    constraints, guess, secret, rounds
                ) }

                assertEquals(fieldsExpected, fieldsActual)
            } catch (e: Exception) {
                Log.e("assertEquals GameOutcomes", " expect $expected")
                Log.e("assertEquals GameOutcomes", " actual $actual")
                throw e
            }
        }
    }

    private fun assertEqualsPerformanceRecord(expected: PerformanceRecord, actual: PerformanceRecord?) {
        Log.d("assertEqualsPerformanceRecord", "expect wins ${expected.winningTurnCounts}")
        Log.d("assertEqualsPerformanceRecord", "expect loss ${expected.losingTurnCounts}")
        Log.d("assertEqualsPerformanceRecord", "expect forf ${expected.forfeitTurnCounts}")
        Log.d("assertEqualsPerformanceRecord", "actual wins ${actual?.winningTurnCounts}")
        Log.d("assertEqualsPerformanceRecord", "actual loss ${actual?.losingTurnCounts}")
        Log.d("assertEqualsPerformanceRecord", "actual forf ${actual?.forfeitTurnCounts}")

        when (expected) {
            is TotalPerformanceRecord -> {
                assertTrue(actual is TotalPerformanceRecord)
                assertEquals(expected.daily, (actual as TotalPerformanceRecord).daily)
            }
            is GameTypePerformanceRecord -> {
                assertTrue(actual is GameTypePerformanceRecord)
                assertEquals(expected.daily, (actual as GameTypePerformanceRecord).daily)
            }
        }

        assertEquals(expected.attempts, actual.attempts)
        assertEquals(expected.wins, actual.wins)
        assertEquals(expected.losses, actual.losses)
        assertEquals(expected.forfeits, actual.forfeits)

        expected.winningTurnCounts.entries.forEach {
            assertEquals(it.value, expected.winningTurnCounts[it.key])
        }
        expected.losingTurnCounts.entries.forEach {
            assertEquals(it.value, expected.losingTurnCounts[it.key])
        }
        expected.forfeitTurnCounts.entries.forEach {
            assertEquals(it.value, expected.forfeitTurnCounts[it.key])
        }
    }

    private fun assertEqualsPlayerStreak(expected: PlayerStreak, actual: PlayerStreak?) {
        when (expected) {
            is GameTypePlayerStreak -> {
                assertTrue(actual is GameTypePlayerStreak)
                assertEquals(expected.type, (actual as GameTypePlayerStreak).type)
            }
        }

        assertEquals(expected.current, actual.current)
        assertEquals(expected.best, actual.best)
        assertEquals(expected.currentDaily, actual.currentDaily)
        assertEquals(expected.bestDaily, actual.bestDaily)
        assertEquals(expected.never, actual.never)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Setup
    //---------------------------------------------------------------------------------------------

    private lateinit var codeWordDb: CodeWordDb

    private val uuids = List<UUID>(20) { UUID.randomUUID() }
    private val types = listOf(
        createGameType(language = CodeLanguage.ENGLISH, length = 5, characterOccurrences = 5, feedback = ConstraintPolicy.ALL),
        createGameType(language = CodeLanguage.ENGLISH, length = 5, characterOccurrences = 1, feedback = ConstraintPolicy.AGGREGATED_INCLUDED),
        createGameType(language = CodeLanguage.ENGLISH, length = 4, characterOccurrences = 4, feedback = ConstraintPolicy.ALL),
        createGameType(language = CodeLanguage.ENGLISH, length = 3, characterOccurrences = 3, feedback = ConstraintPolicy.ALL),
        createGameType(language = CodeLanguage.ENGLISH, length = 6, characterOccurrences = 1, feedback = ConstraintPolicy.AGGREGATED_EXACT),
        createGameType(language = CodeLanguage.CODE, length = 4, characterOccurrences = 4, characters = 6, feedback = ConstraintPolicy.AGGREGATED),
        createGameType(language = CodeLanguage.CODE, length = 4, characterOccurrences = 1, characters = 6, feedback = ConstraintPolicy.AGGREGATED_EXACT),
        createGameType(language = CodeLanguage.CODE, length = 5, characterOccurrences = 4, characters = 6, feedback = ConstraintPolicy.AGGREGATED),
        createGameType(language = CodeLanguage.CODE, length = 6, characterOccurrences = 6, characters = 8, feedback = ConstraintPolicy.AGGREGATED),
    )
    private val outcomes = uuids.subList(0, 7).mapIndexed { index, uuid ->
        val type = types[index % types.size]
        // first 3 have nulls / empties, next 4 have non-nulls / full
        if (index < 3) {
            createGameOutcome(uuid = uuid, type = type, seed = null, guess = null, secret = null, constraints = listOf())
        } else {
            createGameOutcome(
                uuid = uuid,
                type = type,
                seed = "snthaoentuh",
                guess = "tower",
                secret = "teach",
                constraints = listOf(
                    Constraint.create("bluff", "teach"),
                    Constraint.create("pious", "teach"),
                    Constraint.create("reach", "teach")
                )
            )
        }
    }

    @Before
    fun createDatabase() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val db = CodeWordDbImpl(appContext)
        db.resetDatabase()
        codeWordDb = db
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Meta
    //---------------------------------------------------------------------------------------------

    private fun resetDatabase_canary() {
        // a simple test that detects data retention between invocations

        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)

        assertEquals(1, codeWordDb.getTotalPerformanceRecord(solver, evaluator).wins)
        assertEquals(1, codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null).wins)
    }

    @Test
    fun resetDatabase_canary_1() {
        resetDatabase_canary()
    }

    @Test
    fun resetDatabase_canary_2() {
        resetDatabase_canary()
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Outcomes
    //---------------------------------------------------------------------------------------------

    @Test
    fun putOutcome() {
        // with nulls / empties
        val outcome1 = createGameOutcome(seed = null, guess = null, secret = null, constraints = listOf())
        codeWordDb.putOutcome(outcome1)

        // non-nulls / full
        val outcome2 = createGameOutcome(
            type = createGameType(language = CodeLanguage.ENGLISH, length = 5),
            seed = "snthaoentuh",
            guess = "tower",
            secret = "teach",
            constraints = listOf(
                Constraint.create("bluff", "teach"),
                Constraint.create("pious", "teach"),
                Constraint.create("reach", "teach")
            )
        )
        codeWordDb.putOutcome(outcome2)
    }

    @Test
    fun hasOutcome() {
        uuids.forEach { assertFalse(codeWordDb.hasOutcome(it)) }

        // add the 3 with nulls / empties
        uuids.subList(0, 3).forEach {
            val outcome = createGameOutcome(uuid = it, seed = null, guess = null, secret = null, constraints = listOf())
            codeWordDb.putOutcome(outcome)
        }

        uuids.subList(0, 3).forEach { assert(codeWordDb.hasOutcome(it)) }
        uuids.subList(3, uuids.size).forEach { assertFalse(codeWordDb.hasOutcome(it)) }

        // add the next 4 with non-nulls / full
        uuids.subList(3, 7).forEach {
            val outcome = createGameOutcome(
                uuid = it,
                type = createGameType(language = CodeLanguage.ENGLISH, length = 5),
                seed = "snthaoentuh",
                guess = "tower",
                secret = "teach",
                constraints = listOf(
                    Constraint.create("bluff", "teach"),
                    Constraint.create("pious", "teach"),
                    Constraint.create("reach", "teach")
                )
            )
            codeWordDb.putOutcome(outcome)
        }

        uuids.subList(0, 7).forEach { assert(codeWordDb.hasOutcome(it)) }
        uuids.subList(7, uuids.size).forEach { assertFalse(codeWordDb.hasOutcome(it)) }
    }

    @Test
    fun getOutcome() {
        uuids.forEach { assertFalse(codeWordDb.hasOutcome(it)) }

        // getOutcome should be null in all cases
        uuids.forEach { assertNull(codeWordDb.getOutcome(it)) }

        // add the 3 with nulls / empties
        outcomes.subList(0, 3).forEach { codeWordDb.putOutcome(it) }

        // compare retrieved output
        outcomes.subList(0, 3).forEach { outcome ->
            val retrieved = codeWordDb.getOutcome(outcome.uuid)
            assertEqualsGameOutcome(outcome, retrieved)
        }

        // add the next 4
        outcomes.subList(3, 7).forEach { codeWordDb.putOutcome(it) }

        // compare retrieved output
        outcomes.subList(0, 7).forEach { outcome ->
            val retrieved = codeWordDb.getOutcome(outcome.uuid)
            assertEqualsGameOutcome(outcome, retrieved)
        }
        uuids.subList(7, uuids.size).forEach { assertNull(codeWordDb.getOutcome(it)) }
    }

    @Test
    fun getOutcomeOutcome() {
        uuids.forEach { assertFalse(codeWordDb.hasOutcome(it)) }

        // first 3 have nulls / empties, next 4 have non-nulls / full
        val outcomes = uuids.subList(0, 7).mapIndexed { index, uuid ->
            if (index < 3) {
                createGameOutcome(uuid = uuid, seed = null, guess = null, secret = null, constraints = listOf())
            } else {
                createGameOutcome(
                    uuid = uuid,
                    type = createGameType(language = CodeLanguage.ENGLISH, length = 5),
                    seed = "snthaoentuh",
                    guess = "tower",
                    secret = "teach",
                    constraints = listOf(
                        Constraint.create("bluff", "teach"),
                        Constraint.create("pious", "teach"),
                        Constraint.create("reach", "teach")
                    )
                )
            }
        }

        // getOutcome should be null in all cases
        uuids.forEach { assertNull(codeWordDb.getOutcomeOutcome(it)) }

        // add the 3 with nulls / empties
        outcomes.subList(0, 3).forEach { codeWordDb.putOutcome(it) }

        // compare retrieved output
        outcomes.subList(0, 3).forEach { outcome ->
            val retrieved = codeWordDb.getOutcomeOutcome(outcome.uuid)
            assertEquals(outcome.outcome, retrieved?.first)
            assertEquals(outcome.round, retrieved?.second)
        }

        // add the next 4
        outcomes.subList(3, 7).forEach { codeWordDb.putOutcome(it) }

        // compare retrieved output
        outcomes.subList(0, 7).forEach { outcome ->
            val retrieved = codeWordDb.getOutcomeOutcome(outcome.uuid)
            assertEquals(outcome.outcome, retrieved?.first)
            assertEquals(outcome.round, retrieved?.second)
        }
        uuids.subList(7, uuids.size).forEach { assertNull(codeWordDb.getOutcomeOutcome(it)) }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Performance Records
    //---------------------------------------------------------------------------------------------

    @Test
    fun getTotalPerformanceRecord_empty() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        assertEqualsPerformanceRecord(
            createPerformanceRecord(),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )
    }

    @Test
    fun getGameTypePerformanceRecord_empty() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        types.forEachIndexed { index, type ->
            val daily = when (index % 3) {
                0 -> false
                1 -> true
                else -> null
            }
            assertEqualsPerformanceRecord(
                createPerformanceRecord(type = type, daily = daily),
                codeWordDb.getGameTypePerformanceRecord(type, solver, evaluator, daily)
            )
        }
    }

    @Test
    fun getPerformanceRecord_regression_1() {
        // test for a bug exposed in testing, where the "all game types" record would not be
        // correctly updated. Found to be caused by the use of the wrong string literal for
        // the "all" game_type field upon insertion. Note: may overlap with regression_2 cause.

        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.WON, 4)

        val total = codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        assertEquals(3, codeWordDb.getTotalPerformanceRecord(solver, evaluator).attempts)
        assertEquals(2, codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null).attempts)
        assertEquals(1, codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null).attempts)
    }

    @Test
    fun getPerformanceRecord_regression_2() {
        // test for a bug exposed in testing, where records failed to update loss histogram
        // in this specific insertion order. Found to be caused by a mis-use of "+=" operator
        // on IntHistogram (replaces elements rather than summing them). Note: may overlap
        // with regression_1 cause.

        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)

        val expected = createPerformanceRecord(types[0], null, listOf(
            Triple(GameOutcome.Outcome.WON, 4, 3),
            Triple(GameOutcome.Outcome.LOST, 6, 2)
        ))

        val actual = codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)

        assertEquals(5, actual.attempts)
        assertEquals(2, actual.losses)
        assertEqualsPerformanceRecord(expected, actual)
    }

    //region Tests: Performance Records: Add/Remove
    //---------------------------------------------------------------------------------------------

    @Test
    fun getPerformanceRecord_afterAdding() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 0)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 3)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)

        // a few more for type 1
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)

        // one attempt at type 2
        codeWordDb.addToPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 2)

        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 2),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 2),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1),
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 2),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 2),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 2),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.WON, 5, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf(
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf(
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    @Test
    fun getPerformanceRecord_afterAdding_thenSubtracting() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 0)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 3)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)

        // a few more for type 1
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)

        // one attempt at type 2
        codeWordDb.addToPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 2)

        // remove some forfeits and a few wins
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 0)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 2)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 3)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)

        codeWordDb.removeFromPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 5)

        codeWordDb.removeFromPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 2)


        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1),
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 2)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    @Test
    fun getPerformanceRecord_afterAdding_thenSubtracting_thenAdding() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 4)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 0)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 3)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 2)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 4)

        // a few more for type 1
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 4)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 5)
        codeWordDb.addToPerformanceRecords(types[1], solver, evaluator, true, GameOutcome.Outcome.LOST, 6)

        // one attempt at type 2
        codeWordDb.addToPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 2)

        // remove some forfeits and a few wins
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 0)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 2)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 3)
        codeWordDb.removeFromPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.WON, 5)

        codeWordDb.removeFromPerformanceRecords(types[1], solver, evaluator, false, GameOutcome.Outcome.WON, 5)

        codeWordDb.removeFromPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.FORFEIT, 2)

        // add some back in
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.FORFEIT, 1)
        codeWordDb.addToPerformanceRecords(types[0], solver, evaluator, false, GameOutcome.Outcome.LOST, 6)
        codeWordDb.addToPerformanceRecords(types[2], solver, evaluator, true, GameOutcome.Outcome.WON, 3)


        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1),
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 4),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf(
                Triple(GameOutcome.Outcome.WON, 3, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf(
                Triple(GameOutcome.Outcome.WON, 3, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Performance Records: Update
    //---------------------------------------------------------------------------------------------

    @Test
    fun getPerformanceRecord_afterUpdate_add() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 0),
            Pair(GameOutcome.Outcome.FORFEIT, 2),
            Pair(GameOutcome.Outcome.WON, 3),
            Pair(GameOutcome.Outcome.WON, 2),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 6),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.WON, 4)
        ))
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 4)
        ))

        // add for type 1
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 5)
        ))
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6)
        ))

        // add for type 2
        codeWordDb.updatePerformanceRecords(types[2], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.FORFEIT, 2)
        ))

        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 6),
                Triple(GameOutcome.Outcome.WON, 5, 3),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 4),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 2),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 2),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 2),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 2),
                Triple(GameOutcome.Outcome.FORFEIT, 0, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.WON, 5, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf(
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf(
                Triple(GameOutcome.Outcome.FORFEIT, 2, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    @Test
    fun getPerformanceRecord_afterUpdate_addThenSubtract() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 0),
            Pair(GameOutcome.Outcome.FORFEIT, 2),
            Pair(GameOutcome.Outcome.WON, 3),
            Pair(GameOutcome.Outcome.WON, 2),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 6),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.WON, 4)
        ))
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 4)
        ))

        // add for type 1
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 5)
        ))
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6)
        ))

        // add for type 2
        codeWordDb.updatePerformanceRecords(types[2], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.FORFEIT, 2)
        ))

        // remove some forfeits and a few wins
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, false, remove = listOf(
            Pair(GameOutcome.Outcome.FORFEIT, 0),
            Pair(GameOutcome.Outcome.FORFEIT, 2),
            Pair(GameOutcome.Outcome.WON, 3),
            Pair(GameOutcome.Outcome.WON, 5)
        ))

        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, false, remove = listOf(
            Pair(GameOutcome.Outcome.WON, 5)
        ))

        codeWordDb.updatePerformanceRecords(types[2], solver, evaluator, true, remove = listOf(
            Pair(GameOutcome.Outcome.FORFEIT, 2)
        ))


        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1),
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 2)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    @Test
    fun getPerformanceRecord_afterUpdate_addThenUpdate() {
        val solver = GameSetup.Solver.PLAYER
        val evaluator = GameSetup.Evaluator.HONEST

        // add for type 0
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 0),
            Pair(GameOutcome.Outcome.FORFEIT, 2),
            Pair(GameOutcome.Outcome.WON, 3),
            Pair(GameOutcome.Outcome.WON, 2),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 5),
            Pair(GameOutcome.Outcome.WON, 6),
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.WON, 4)
        ))
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.LOST, 6),
            Pair(GameOutcome.Outcome.FORFEIT, 4)
        ))

        // add for type 1
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, false, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.WON, 5)
        ))
        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.WON, 4),
            Pair(GameOutcome.Outcome.LOST, 6)
        ))

        // add for type 2
        codeWordDb.updatePerformanceRecords(types[2], solver, evaluator, true, add = listOf(
            Pair(GameOutcome.Outcome.FORFEIT, 2)
        ))

        // remove some forfeits and a few wins, add a few back in
        codeWordDb.updatePerformanceRecords(types[0], solver, evaluator, false,
            add = listOf(
                Pair(GameOutcome.Outcome.FORFEIT, 1),
                Pair(GameOutcome.Outcome.LOST, 6)
            ),
            remove = listOf(
                Pair(GameOutcome.Outcome.FORFEIT, 0),
                Pair(GameOutcome.Outcome.FORFEIT, 2),
                Pair(GameOutcome.Outcome.WON, 3),
                Pair(GameOutcome.Outcome.WON, 5)
            )
        )

        codeWordDb.updatePerformanceRecords(types[1], solver, evaluator, false, remove = listOf(
            Pair(GameOutcome.Outcome.WON, 5)
        ))

        codeWordDb.updatePerformanceRecords(types[2], solver, evaluator, true,
            add = listOf(
                Pair(GameOutcome.Outcome.WON, 3)
            ),
            remove = listOf(
                Pair(GameOutcome.Outcome.FORFEIT, 2)
            )
        )


        assertEqualsPerformanceRecord(
            createPerformanceRecord(daily = null, results = listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 3, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1),
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], null, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 4),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], true, listOf(
                Triple(GameOutcome.Outcome.LOST, 6, 1),
                Triple(GameOutcome.Outcome.FORFEIT, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[0], false, listOf(
                Triple(GameOutcome.Outcome.WON, 2, 1),
                Triple(GameOutcome.Outcome.WON, 4, 4),
                Triple(GameOutcome.Outcome.WON, 5, 1),
                Triple(GameOutcome.Outcome.WON, 6, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 3),
                Triple(GameOutcome.Outcome.FORFEIT, 1, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[0], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], null, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 2),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], true, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1),
                Triple(GameOutcome.Outcome.LOST, 6, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[1], false, listOf(
                Triple(GameOutcome.Outcome.WON, 4, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[1], solver, evaluator, false)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], null, listOf(
                Triple(GameOutcome.Outcome.WON, 3, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, null)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], true, listOf(
                Triple(GameOutcome.Outcome.WON, 3, 1)
            )),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, true)
        )

        assertEqualsPerformanceRecord(
            createPerformanceRecord(types[2], false, listOf()),
            codeWordDb.getGameTypePerformanceRecord(types[2], solver, evaluator, false)
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tests: Player Streaks
    //---------------------------------------------------------------------------------------------

    @Test
    fun getPlayerStreak_empty() {
        types.forEach {
            val expected = createPlayerStreak(it)
            val actual = codeWordDb.getPlayerStreak(it)
            assertEqualsPlayerStreak(expected, actual)
        }
    }

    @Test
    fun getPlayerStreak_afterWins() {
        // type 0: 7 wins, including 2 dailies
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)

        // type 1: 2 wins, no dailies
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)

        // type 2: 4 daily wins, no otherwise
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)

        // type 3: 1 daily win, 1 normal win
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)

        // type 0 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[0], current = 7, currentDaily = 2),
            codeWordDb.getPlayerStreak(types[0])
        )

        // type 1 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[1], current = 2, currentDaily = 0),
            codeWordDb.getPlayerStreak(types[1])
        )

        // type 2 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[2], current = 4, currentDaily = 4),
            codeWordDb.getPlayerStreak(types[2])
        )

        // type 3 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[3], current = 2, currentDaily = 1),
            codeWordDb.getPlayerStreak(types[3])
        )

        // no other streaks
        types.subList(4, types.size).forEach {
            val expected = createPlayerStreak(it)
            val actual = codeWordDb.getPlayerStreak(it)
            assertEqualsPlayerStreak(expected, actual)
        }
    }

    @Test
    fun getPlayerStreak_afterWinsAndLosses() {
        // type 0: 7 wins, including 2 dailies
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)

        // type 1: 2 wins, no dailies
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)

        // type 2: 4 daily wins, no otherwise
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)

        // type 3: 1 daily win, 1 normal win
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)

        // losses in types 0, 2, 3
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.LOST)

        // type 0 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[0], current = 0, best = 7, currentDaily = 2),
            codeWordDb.getPlayerStreak(types[0])
        )

        // type 1 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[1], current = 2, currentDaily = 0),
            codeWordDb.getPlayerStreak(types[1])
        )

        // type 2 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[2], current = 0, best = 4, currentDaily = 4),
            codeWordDb.getPlayerStreak(types[2])
        )

        // type 3 streak
        assertEqualsPlayerStreak(
            createPlayerStreak(types[3], current = 0, best = 2, currentDaily = 0, bestDaily = 1),
            codeWordDb.getPlayerStreak(types[3])
        )

        // no other streaks
        types.subList(4, types.size).forEach {
            val expected = createPlayerStreak(it)
            val actual = codeWordDb.getPlayerStreak(it)
            assertEqualsPlayerStreak(expected, actual)
        }
    }

    @Test
    fun getPlayerStreak_afterStreaks() {
        // type 0: 7 wins, including 2 dailies
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)

        // type 1: 2 wins, no dailies
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)

        // type 2: 4 daily wins, no otherwise
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)

        // type 3: 1 daily win, 1 normal win
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)

        // losses in types 0, 2, 3
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.LOST)

        // type 0: play a few more times
        // 0/7, daily 2/2
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[0], true, GameOutcome.Outcome.WON)
        // 2/7, daily 2/3
        assertEqualsPlayerStreak(
            createPlayerStreak(types[0], current = 2, best = 7, currentDaily = 2, bestDaily = 3),
            codeWordDb.getPlayerStreak(types[0])
        )

        // type 1: flail around, win a few, flail around
        // 2/2, daily 0/0
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[1], true, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[1], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[1], false, GameOutcome.Outcome.LOST)
        // 0/2, 1/1
        assertEqualsPlayerStreak(
            createPlayerStreak(types[1], current = 0, best = 2, currentDaily = 1, bestDaily = 1),
            codeWordDb.getPlayerStreak(types[1])
        )

        // type 2: never lose a daily
        // 0/4, daily 4/4
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[2], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[2], true, GameOutcome.Outcome.WON)
        // 1/4, 7/7
        assertEqualsPlayerStreak(
            createPlayerStreak(types[2], current = 1, best = 4, currentDaily = 7, bestDaily = 7),
            codeWordDb.getPlayerStreak(types[2])
        )

        // type 3: win some, lose some
        // 0/2, daily 0/1
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], false, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.LOST)
        codeWordDb.updatePlayerStreak(types[3], true, GameOutcome.Outcome.WON)
        // 1/3, 1/3
        assertEqualsPlayerStreak(
            createPlayerStreak(types[3], current = 1, best = 4, currentDaily = 1, bestDaily = 2),
            codeWordDb.getPlayerStreak(types[3])
        )

        // type 4: a win! (daily)
        codeWordDb.updatePlayerStreak(types[4], true, GameOutcome.Outcome.WON)
        assertEqualsPlayerStreak(
            createPlayerStreak(types[4], current = 1, best = 1, currentDaily = 1, bestDaily = 1),
            codeWordDb.getPlayerStreak(types[4])
        )

        // type 5: a win! (not daily)
        codeWordDb.updatePlayerStreak(types[5], false, GameOutcome.Outcome.WON)
        assertEqualsPlayerStreak(
            createPlayerStreak(types[5], current = 1, best = 1, currentDaily = 0, bestDaily = 0),
            codeWordDb.getPlayerStreak(types[5])
        )

        // type 6: a loss! (daily)
        codeWordDb.updatePlayerStreak(types[6], true, GameOutcome.Outcome.LOST)
        assertEqualsPlayerStreak(
            createPlayerStreak(types[6], current = 0, best = 0, currentDaily = 0, bestDaily = 0),
            codeWordDb.getPlayerStreak(types[6])
        )

        // type 7: a loss! (not daily)
        codeWordDb.updatePlayerStreak(types[7], false, GameOutcome.Outcome.LOST)
        assertEqualsPlayerStreak(
            createPlayerStreak(types[7], current = 0, best = 0, currentDaily = 0, bestDaily = 0),
            codeWordDb.getPlayerStreak(types[7])
        )

        // no other streaks
        types.subList(8, types.size).forEach {
            val expected = createPlayerStreak(it)
            val actual = codeWordDb.getPlayerStreak(it)
            assertEqualsPlayerStreak(expected, actual)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}