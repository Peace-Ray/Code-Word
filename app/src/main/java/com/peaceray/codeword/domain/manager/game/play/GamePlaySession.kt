package com.peaceray.codeword.domain.manager.game.play

import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.Settings

/**
 * A "GamePlaySession" contains a Game and related structures, especially Evaluator and Solver,
 * making available a modified version of their interfaces that supports threading and
 * coroutine suspension.
 *
 * Because the GamePlaySession is dealing internally with mutable structures, most functions
 * are implemented as "suspend fun" to help keep threading manageable and responsive. For
 * instance [getGameState] is a simple accessor into a Game property, but that property can
 * be mutated by other suspend functions provided by this interface in a way that is thread-unsafe
 * if not managed.
 */
interface GamePlaySession {

    //region Game Data Accessors
    //-----------------------------------------------------------------------------------------

    val seed: String?

    val gameSetup: GameSetup

    suspend fun getConstraints(): List<Constraint>

    suspend fun getCurrentGuess(): String?

    suspend fun getCurrentMoves(): Pair<String?, List<Constraint>>

    suspend fun getSettings(): Settings

    suspend fun getGameSaveData(): GameSaveData

    suspend fun getGameState(): Game.State

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Persistence
    //-----------------------------------------------------------------------------------------

    suspend fun save()

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Play: User-Driven
    //-----------------------------------------------------------------------------------------

    @Throws(IllegalStateException::class, Game.IllegalGuessException::class)
    suspend fun advanceWithGuess(candidate: String)

    @Throws(IllegalStateException::class, Game.IllegalEvaluationException::class)
    suspend fun advanceWithEvaluation(constraint: Constraint)

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Play: Session-Driven
    //-----------------------------------------------------------------------------------------

    val canGenerateGuesses: Boolean
    val canGenerateEvaluations: Boolean
    val canGenerateSolutions: Boolean

    @Throws(IllegalStateException::class, Game.IllegalGuessException::class, UnsupportedOperationException::class)
    suspend fun generateGuess(advance: Boolean = false): String

    @Throws(IllegalStateException::class, Game.IllegalEvaluationException::class, UnsupportedOperationException::class)
    suspend fun generateEvaluation(advance: Boolean = false): Constraint

    @Throws(IllegalStateException::class, Game.IllegalGuessException::class, UnsupportedOperationException::class)
    suspend fun generateSolution(advance: Boolean = false): String

    //-----------------------------------------------------------------------------------------
    //endregion

}