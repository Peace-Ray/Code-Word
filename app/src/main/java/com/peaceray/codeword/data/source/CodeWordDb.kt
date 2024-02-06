package com.peaceray.codeword.data.source

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.GameTypePerformanceRecord
import com.peaceray.codeword.data.model.record.GameTypePlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import com.peaceray.codeword.game.bot.Evaluator
import java.util.UUID

/**
 * A Database interface for storing Code Word game outcomes and performance.
 *
 * This interface does not ensure consistency between tables, or rows-within-tables. Business
 * logic should be applied externally. For instance, a game ending with a GameOutcome should
 * be recorded with [putOutcome], but also affects performance records and player streaks, and
 * calls to [updatePlayerStreak] and [updatePerformanceRecords] are appropriate.
 */
interface CodeWordDb {

    //region Game Outcomes
    //---------------------------------------------------------------------------------------------

    /**
     * Put the provided GameOutcome in the database, either inserting a row or updating as necessary.
     * UUID is a unique identifier for a Game, which can only have one GameOutcome (although the
     * outcome itself might update over time, if a game is "forfeited" but later resumed).
     */
    fun putOutcome(outcome: GameOutcome)

    /**
     * Is there a GameOutcome associated with the given UUID?
     */
    fun hasOutcome(uuid: UUID): Boolean

    /**
     * Retrieve the result of the indicated game, if a GameOutcome has been recorded:
     * a Pair giving GameOutcome.Outcome (the result of the game) and the round that GameOutcome.Outcome
     * was decided.
     *
     * Unfortunately named.
     */
    fun getOutcomeOutcome(uuid: UUID): Pair<GameOutcome.Outcome, Int>?

    /**
     * Retrieve the GameOutcome previously recorded for this UUID, if any.
     */
    fun getOutcome(uuid: UUID): GameOutcome?

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Performance Records.
    //---------------------------------------------------------------------------------------------

    /**
     * Update performance records with a list of changes. [add] gives the GameOutcome.Outcome and
     * deciding round of a series of games to add to the records; [remove] gives the same, for
     * games that were already included in the records but should now be removed.
     */
    fun updatePerformanceRecords(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator, daily: Boolean, add: List<Pair<GameOutcome.Outcome, Int>> = emptyList(), remove: List<Pair<GameOutcome.Outcome, Int>> = emptyList())

    /**
     * Update performance records with a single new game outcome. Equivalent to [updatePerformanceRecords]
     * with a single-item list as [add].
     */
    fun addToPerformanceRecords(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator, daily: Boolean, outcome: GameOutcome.Outcome, round: Int)

    /**
     * Update performance records by removing a single game outcome. Equivalent to
     * [updatePerformanceRecords] with a single-item list as [remove]
     */
    fun removeFromPerformanceRecords(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator, daily: Boolean, outcome: GameOutcome.Outcome, round: Int)

    /**
     * Retrieve the TotalPerformanceRecord for all games played with the indicated settings.
     */
    fun getTotalPerformanceRecord(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): TotalPerformanceRecord

    /**
     * Retrieve the GameTypePerformanceRecord for games played with the indicated settings.
     * If [daily] is null, includes both daily and non-daily games.
     */
    fun getGameTypePerformanceRecord(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator, daily: Boolean?): GameTypePerformanceRecord

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Player Streaks
    //---------------------------------------------------------------------------------------------

    /**
     * Update the player streak by recording the indicated GameOutcome.Outcome.
     */
    fun updatePlayerStreak(gameType: GameType, daily: Boolean, outcome: GameOutcome.Outcome)

    /**
     * Retrieve the player streak.
     */
    fun getPlayerStreak(gameType: GameType): GameTypePlayerStreak

    //---------------------------------------------------------------------------------------------
    //endregion
}