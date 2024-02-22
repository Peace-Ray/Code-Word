package com.peaceray.codeword.presentation.manager.feedback

import com.peaceray.codeword.data.manager.game.play.GamePlaySession
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup

/**
 * A [GameFeedbackProvider] creates Guesses and other user-visible feedback from Constraints and
 * partial guess strings. The specific functionality of a Provider is dictated by multiple factors,
 * including the Game settings used to create the game ([GameSetup]) and "Hint / No Hint" settings.
 * This Manager provides Provider instances upon request based on those settings.
 */
interface GameFeedbackManager {

    //region GameFeedbackProvider Creation
    //-----------------------------------------------------------------------------------------

    suspend fun getGameFeedbackProvider(gameSetup: GameSetup, hints: Boolean): GameFeedbackProvider

    suspend fun getGameFeedbackProvider(gameSaveData: GameSaveData, hints: Boolean): GameFeedbackProvider

    //-----------------------------------------------------------------------------------------
    //endregion

    //region GameSetup Evaluation
    //-----------------------------------------------------------------------------------------

    fun supportsHinting(gameSetup: GameSetup): Boolean

    fun supportsHinting(gameSaveData: GameSaveData): Boolean

    //-----------------------------------------------------------------------------------------
    //endregion

}