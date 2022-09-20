package com.peaceray.codeword.data.model.record

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.game.data.Constraint
import java.util.*

/**
 * A somewhat redundant data object encapsulating seed, game setup, and game state
 * (i.e.: the basic content of [GameSaveData]) but conditional on the game itself
 * ending. In other words, there is no need to allow game sessions to be resumed
 * from this data, nor to maintain compatibility with future Game objects in later updates.
 */
class GameOutcome(
    val uuid: UUID,
    val type: GameType,
    val daily: Boolean,
    val hard: Boolean,
    val solver: GameSetup.Solver,
    val evaluator: GameSetup.Evaluator,
    val seed: String?,
    val outcome: Outcome,
    val round: Int,
    val constraints: List<Constraint>,
    val guess: String?,
    val secret: String?,
    val rounds: Int,
    val recordedAt: Date
) {
    enum class Outcome { WON, LOST, FORFEIT, LOADING }
}