package com.peaceray.codeword.domain.manager.game

import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.data.Settings

interface GameSessionManager {

    //region Game Setup
    //-----------------------------------------------------------------------------------------

    /**
     * Construct and return a [Game] instance based on the provided setup. If appropriate, the
     * game will include [Game.constraints] and [Game.currentGuess] from a persisted session;
     * to avoid this and always create a Game with no moves yet taken, use [create] = true.
     *
     * This call may take a while to execute and involve IO operations (especially if [create]
     * is not true). It is recommended to execute this call off the main thread.
     *
     * @param setup The GameSetup to use in instance configuration.
     * @param create Whether to create the new game in a "first-move" state, regardless of
     * persisted record.
     */
    fun getGame(seed: String?, setup: GameSetup, create: Boolean = false): Game

    /**
     * Construct and return a [Game] instance based on the provided GameSaveData, including
     * (if appropriate) [Game.constraints] and [Game.currentGuess] from the saved session.
     *
     * This function does not perform I/O, but may take a while to replay moves during construction.
     *
     * @param save The GameSaveData to transform into a Game
     */
    fun getGame(save: GameSaveData): Game

    /**
     * Construct and return a [Settings] instance based on the provided setup.
     *
     * @param setup The GameSetup to use in instance configuration.
     */
    fun getSettings(setup: GameSetup): Settings

    /**
     * Construct and return a [Solver] based on the provided setup. It is an error to call this
     * function when [setup.solver] is [GameSetup.Solver.PLAYER].
     *
     * This call may take a while to execute and involve IO operations. It is recommended to
     * execute this call off the main thread.
     *
     * @param setup The GameSetup to use in instance configuration.
     * @return A Solver instance appropriate for the game
     */
    fun getSolver(setup: GameSetup): Solver

    /**
     * Construct and return an [Evaluator] based on the provided setup. It is an error to call this
     * function when [setup.evaluator] is [GameSetup.Evaluator.PLAYER].
     *
     * This call may take a while to execute and involve IO operations. It is recommended to
     * execute this call off the main thread.
     *
     * @param setup The GameSetup to use in instance configuration.
     * @return An Evaluation instance appropriate for the game
     */
    fun getEvaluator(setup: GameSetup): Evaluator

    /**
     * Return an [Iterable] of [Char]s showing the characters allowed by the game.
     *
     * @param setup The GameSetup to use in instance configuration
     * @return An Iterable of characters representing the valid code character set.
     */
    fun getCodeCharacters(setup: GameSetup): Iterable<Char>

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Persistence
    //-----------------------------------------------------------------------------------------

    /**
     * Load the persisted game state; determine if currently waiting for a guess or evaluaton,
     * or if over. More efficient than [loadGame] for determining the current game status.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     * @return The current game state, if found, or null.
     */
    fun loadState(seed: String? = null, setup: GameSetup? = null): Game.State?

    /**
     * Loads the persisted game described by the setup, if available. Throws an exception if IO
     * access fails, but not if the saved Game simply can't be found.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     * @return The GameSaveData at its most recently saved state, or null if not available.
     */
    fun loadSave(seed: String? = null, setup: GameSetup? = null): GameSaveData?

    /**
     * Loads the persisted game described by the setup, if available. Throws an exception if IO
     * access fails, but not if the saved Game simply can't be found.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     * @return The GameSaveData and Game at its most recently saved state, or null if not available.
     */
    fun loadGame(seed: String? = null, setup: GameSetup? = null): Pair<GameSaveData, Game>?

    /**
     * Persist the current state of the game; after this call, [getGame] will return a Game instance
     * at this move state (if an identical [setup] is provided).
     *
     * Do not continue the [Game] until this call returns.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     */
    fun saveGame(seed: String?, setup: GameSetup, game: Game)

    /**
     * Persist the current state of the game; after this call, [getGame] will return a Game instance
     * at this move state (if an identical [setup] is provided).
     *
     * This call is safe to perform asynchronously while the [Game] continues.
     *
     * @param saveData Immutable game state save data
     */
    fun saveGame(saveData: GameSaveData)

    /**
     * Clear any persisted games from the record; after this call, [getGame] will return
     * a Game instance with no moves applied (even if an identical [setup] to previously
     * persisted game is provided).
     */
    fun clearSavedGames()

    /**
     * Clear any persisted game(s) from the record that match the provided seed and/or setup.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     */
    fun clearSavedGame(seed: String?, setup: GameSetup)

    //-----------------------------------------------------------------------------------------
    //endregion
}