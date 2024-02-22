package com.peaceray.codeword.data.manager.record

import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.GameTypePerformanceRecord
import com.peaceray.codeword.data.model.record.GameTypePlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import com.peaceray.codeword.game.Game
import java.util.*

/**
 * A Manager for game "Records": performance over time in specific game types, a history of games
 * with guesses, etc.
 */
interface GameRecordManager {

    //region Database Updates
    //---------------------------------------------------------------------------------------------

    /**
     * Record the result of a completed game.
     *
     * @param seed The seed used to create this game, if any
     * @param setup The GameSetup
     * @param game The Game that just finished
     * @param secret The secret for this game, if known
     */
    suspend fun record(seed: String?, setup: GameSetup, game: Game, secret: String?)

    /**
     * Record the result of a completed game.
     *
     * @param gameSaveData The complete save data for the game
     * @param secret The secret, if known
     */
    suspend fun record(gameSaveData: GameSaveData, secret: String?)

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Performance Queries
    //---------------------------------------------------------------------------------------------

    /**
     * Returns whether a GameOutcome exists for the indicated game UUID.
     */
    suspend fun hasOutcome(uuid: UUID): Boolean

    /**
     * Retrieve a GameOutcome for the indicated game UUID, or 'null' if no such outcome
     * exists.
     *
     * @param uuid The unique game identifier
     */
    suspend fun getOutcome(uuid: UUID): GameOutcome?

    /**
     * Get a PerformanceRecord for all games of all types with the indicated roles
     */
    suspend fun getTotalPerformance(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): TotalPerformanceRecord

    /**
     * Retrieve a performance record for games of this setup.
     *
     * @param setup The GameSetup in question
     * @param strict Whether to retrieve a record that strictly matches the setup as closely
     * as possible. For example, for dailies [strict] = false retrieves results of both daily
     * puzzles and non-dailies with equivalent settings.
     */
    suspend fun getPerformance(setup: GameSetup, strict: Boolean): GameTypePerformanceRecord

    /**
     * Retrieve a performance record for games of this type.
     *
     * @param outcome The GameOutcome with the setup in question
     * @param strict Whether to retrieve a record that strictly matches the setup as closely
     * as possible. For example, for dailies [strict] = false retrieves results of both daily
     * puzzles and non-dailies with equivalent settings.
     */
    suspend fun getPerformance(outcome: GameOutcome, strict: Boolean): GameTypePerformanceRecord

    /**
     * Retrieve a record of win streaks for the player on this game type. Not all streaks are
     * recorded.
     *
     * @param setup The GameSetup in question
     */
    suspend fun getPlayerStreak(setup: GameSetup): GameTypePlayerStreak

    /**
     * Retrieve a record of win streaks for the player on this game type. Not all streaks are
     * recorded.
     *
     * @param outcome The GameOutcome with the setup in question
     */
    suspend fun getPlayerStreak(outcome: GameOutcome): GameTypePlayerStreak

    //---------------------------------------------------------------------------------------------
    //endregion

}