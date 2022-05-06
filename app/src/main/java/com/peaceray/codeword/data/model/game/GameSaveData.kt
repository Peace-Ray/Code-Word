package com.peaceray.codeword.data.model.game

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.Settings

/**
 * A snapshot of a [Game] sufficient to save its state for later recreation. The internal state of
 * a [Game] is mutable, but this save data is not, meaning this object can be created from a Game
 * and sent to be saved while the Game itself continues.
 */
data class GameSaveData(val seed: String?, val setup: GameSetup, val settings: Settings, val constraints: List<Constraint>, val currentGuess: String?) {
    constructor(
        seed: String?,
        setup: GameSetup,
        game: Game
    ): this(seed, setup, game.settings, game.constraints, game.currentGuess)
}