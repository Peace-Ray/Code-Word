package com.peaceray.codeword.data.model.record

import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.utils.histogram.IntHistogram

/**
 * A record of game performance: attempts, wins, losses, etc. Also contains a histogram
 * (sparse int array) of the turns on which games ended.
 *
 * @property attempts The number of times a game has been launched
 * @property wins The number of times a game has been won by the guesser
 * @property losses The number of times a game has been lost by the guesser (running out of chances)
 * @property forfeits The number of times a game has been forfeit by the guesser (usually by starting
 * a new game before the previous game ends)
 * @property winningTurnCounts The turn on which winning games ended, and the number of games that
 * were won on that turn. The map, representing a sparse array, is zero-indexed. A value of
 * `winningTurnCount[0] == 5` means that five games have been won in the first move (on Turn 1)
 * @property losingTurnCounts The turn on which losing games ended, and the number of games that
 * were lost on that turn. The map, representing a sparse array, is zero-indexed. A value of
 * `losingTurnCounts[3] == 2` means that two games have been lost on the fourth move (on Turn 4).
 * Put another way, that two games set to be 4 rounds long have been lost, each with four incorrect
 * guesses.
 * @property forfeitTurnCounts The turn on which forfeit games were forfeited, and the number of games
 * that were forfeit on that turn. The map, representing a sparse array, is zero-indexed. A value of
 * `forfeitTurnCounts[2] = 11` means that eleven games have been forfeit on the third move (while
 * waiting for the third guess to be input).
 */
sealed class PerformanceRecord {
    val attempts: Int
        get() = wins + losses + forfeits

    val wins get() = winningTurnCounts.total
    val losses get() = losingTurnCounts.total
    val forfeits get() = forfeitTurnCounts.total

    val winningTurnCounts = IntHistogram(min = 0)
    val losingTurnCounts = IntHistogram(min = 0)
    val forfeitTurnCounts = IntHistogram(min = 0)
}

class TotalPerformanceRecord(val daily: Boolean?): PerformanceRecord()

/**
 * Holds a record of game performances for a specific game type: total attempts,
 * total wins and losses, turn histogram for wins, losses, and forfeits
 *
 */
class GameTypePerformanceRecord(val type: GameType, val daily: Boolean?): PerformanceRecord()