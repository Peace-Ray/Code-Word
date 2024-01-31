package com.peaceray.codeword.domain.manager.game.persistence.impl.legacy

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.Settings
import java.util.*

/**
 * A snapshot of a [Game] sufficient to save its state for later recreation. The internal state of
 * a [Game] is mutable, but this save data is not, meaning this object can be created from a Game
 * and sent to be saved while the Game itself continues.
 */
data class V01GameSaveData(val seed: String?, val setup: V01GameSetup, val settings: Settings, val constraints: List<Constraint>, val currentGuess: String?, val uuid: UUID) {
    constructor(
        seed: String?,
        setup: V01GameSetup,
        game: Game
    ): this(seed, setup, game.settings, game.constraints, game.currentGuess, game.uuid)

    val state: Game.State
        get() = when {
            constraints.any { it.correct } -> Game.State.WON
            constraints.size == settings.rounds -> Game.State.LOST
            currentGuess != null -> Game.State.EVALUATING
            else -> Game.State.GUESSING
        }

    val started: Boolean
        get() = over || currentGuess != null || round > 1

    val won: Boolean
        get() = state == Game.State.WON

    val lost: Boolean
        get() = state == Game.State.LOST

    val over: Boolean
        get() = won || lost

    val round: Int
        get() { return if (over) constraints.size else constraints.size + 1 }
}