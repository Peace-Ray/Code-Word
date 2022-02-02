package com.peaceray.codeword.domain.managers.game

import com.peaceray.codeword.data.model.game.GameSetup

/**
 * A Manager for retrieving [GameSetup] objects describing the active game.
 * They may be situation-dependent, describe the default game, etc. The most basic call
 * is [getSetup] which in practice should provide the session information most directly
 * relevant to the calling context.
 */
interface GameSetupManager {
    /**
     * Returns the appropriate GameSessionSetup
     */
    fun getSetup(): GameSetup
}