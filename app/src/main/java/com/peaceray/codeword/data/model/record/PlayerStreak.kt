package com.peaceray.codeword.data.model.record

import com.peaceray.codeword.data.model.game.GameType

sealed class PlayerStreak {
    var current: Int = 0
    var best: Int = 0
    var currentDaily: Int = 0
    var bestDaily: Int = 0
    val never
        get() = best == 0
}

class GameTypePlayerStreak(val type: GameType): PlayerStreak()