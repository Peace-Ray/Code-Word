package com.peaceray.codeword.data.manager.game.persistence

import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.Game

interface GamePersistenceManager {

    //region Game Persistence
    //-----------------------------------------------------------------------------------------

    /**
     * Load the persisted game state; i.e. determine if currently waiting for a guess or evaluation,
     * or if over. May be more efficient than [load] for determining the current game status.
     *
     * @param setup The GameSetup describing the game settings. Will be compared against the
     * loaded game (if found) to verify a match.
     * @param seeds The seed strings identifying the game, if any.
     * @return The current game state, if found, or null.
     */
    suspend fun loadState(setup: GameSetup? = null, vararg seeds: String?): Game.State?

    /**
     * Loads the persisted game save data described by the setup, if available. May throw an
     * exception if IO access fails, but not if the saved Game simply can't be found.
     *
     * @param setup The GameSetup describing the game settings. Will be compared against the
     *      * loaded game (if found) to verify a match.
     *      * @param seeds The seed strings identifying the game, if any.
     * @return The GameSaveData at its most recently saved state, or null if not available.
     */
    suspend fun load(setup: GameSetup? = null, vararg seeds: String?): GameSaveData?

    /**
     * Persist the current state of the game; after this call, [load] will return an identical
     * GameSaveData instance.
     *
     * This call is safe to perform asynchronously while the [Game] continues.
     *
     * @param saveData Immutable game state save data
     */
    suspend fun save(saveData: GameSaveData)

    /**
     * Clear any persisted games from the record; after this call, [load] will return null for
     * all GameSetups.
     */
    suspend fun clearSaves()

    /**
     * Clear any persisted game(s) from the record that match the provided seed and/or setup.
     *
     * @param seed The seed string identifying the game, if any
     * @param setup The GameSetup describing the game settings
     */
    suspend fun clearSave(seed: String?, setup: GameSetup)

    //-----------------------------------------------------------------------------------------
    //endregion
}