package com.peaceray.codeword.domain.manager.game.play

import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup

/**
 * A Manager that handles creation and manipulation of Game objects and related structures
 * (especially Solvers and Evaluators), providing modified interfaces for them that are thread-safe
 * and support coroutine suspension.
 *
 * Two things to note.
 *
 * First, as the main purpose of the GamePlayManager is to provide an interface through which mutable
 * object(s) are manipulated, the Manager itself is primarily used to create instances of
 * the GamePlaySession class, after which the GamePlaySession object provides the primary interface.
 *
 * Second, it may be desirable to create a GamePlaySession synchronously with some main-view
 * setup code (i.e. without launching a Coroutine). For that purpose, use functions which all
 * a [CoroutineScope] to be passed in as an argument (it will be used for the creation of deferred
 * properties). Such functions are PENDING.
 */
interface GamePlayManager {

    //region GamePlaySession Creation
    //-----------------------------------------------------------------------------------------

    suspend fun getGamePlaySession(seed: String?, gameSetup: GameSetup): GamePlaySession

    suspend fun getGamePlaySession(gameSaveData: GameSaveData): GamePlaySession

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Modification
    //-----------------------------------------------------------------------------------------

    suspend fun canUpdateGamePlaySession(session: GamePlaySession, update: GameSetup): Boolean

    @Throws(IllegalArgumentException::class)
    suspend fun getUpdatedGamePlaySession(session: GamePlaySession, update: GameSetup): GamePlaySession

    //-----------------------------------------------------------------------------------------
    //endregion

}