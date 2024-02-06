package com.peaceray.codeword.data.manager.game.creation

import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.data.Settings

/**
 * A Manager for creating Game instances and related classes, such as "player" classes
 * (Evaluator and Solver). Also creates intermediate data structures such as Settings,
 * CodeCharacter lists, etc. that are used in the creation of those classes but may be
 * independently useful.
 *
 * Essentially, converts GameSetups into Games and/or classes related to Games, but does not
 * manage actual gameplay.
 */
interface GameCreationManager {

    //region Game Creation
    //-----------------------------------------------------------------------------------------

    /**
     * Construct and return a [Game] instance based on the provided setup. The Game will be
     * in an initialized state, with no moves yet taken.
     *
     * This call requires the creation of a Validator, which possibly performs local IO.
     *
     * @param setup The GameSetup to use in instance configuration.
     * @return A Game in an initialized state, ready for play.
     */
    suspend fun createGame(setup: GameSetup): Game

    /**
     * Construct and return a [Game] instance based on the provided setup. If a persisted game
     * session matching the inputs is found, it will be read from disk, restored, and returned.
     * If not, the returned Game will be in an initialized state with no moves yet taken. To
     * always create a new Game instead of loading a saved session, use [createGame].
     *
     * This call may take a while to execute and involve IO operations (especially if [create]
     * is not true).
     *
     * @param seed The seed string for the Game, if any.
     * @param setup The GameSetup to use in instance configuration.
     * @return A Game, which is either complete or ready for play.
     */
    suspend fun getGame(seed: String?, setup: GameSetup): Game

    /**
     * Construct and return a [Game] instance based on the provided GameSaveData, including
     * (if appropriate) [Game.constraints] and [Game.currentGuess] from the saved session.
     *
     * This function does not perform I/O, but converting save data to an active Game requires
     * rerunning moves which may require noticeable computation time.
     *
     * @param save The GameSaveData to transform into a Game
     */
    suspend fun getGame(save: GameSaveData): Game

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Examination
    //-----------------------------------------------------------------------------------------

    /**
     * Construct and return a [Settings] instance based on the provided setup.
     *
     * @param setup The GameSetup to use in instance configuration.
     */
    fun getSettings(setup: GameSetup): Settings

    /**
     * Return an [Iterable] of [Char]s showing the characters allowed by the game.
     *
     * @param setup The GameSetup to use in instance configuration
     * @return An Iterable of characters representing the valid code character set.
     */
    fun getCodeCharacters(setup: GameSetup): Iterable<Char>

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Players
    //-----------------------------------------------------------------------------------------

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
    suspend fun getSolver(setup: GameSetup): Solver

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
    suspend fun getEvaluator(setup: GameSetup): Evaluator

    //-----------------------------------------------------------------------------------------
    //endregion

}