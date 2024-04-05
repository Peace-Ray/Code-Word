package com.peaceray.codeword.data.manager.game.play.impl

import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.game.play.GamePlayManager
import com.peaceray.codeword.data.manager.game.play.GamePlaySession
import com.peaceray.codeword.data.model.game.save.GamePlayData
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.glue.ForComputation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GamePlayManagerImpl @Inject constructor(
    private val gameCreationManager: GameCreationManager,
    private val gamePersistenceManager: GamePersistenceManager,
    @ForComputation private val computationDispatcher: CoroutineDispatcher
): GamePlayManager {

    //region GamePlaySession Creation
    //-----------------------------------------------------------------------------------------

    override suspend fun getGamePlaySession(seed: String?, gameSetup: GameSetup): GamePlaySession {
        val save = gamePersistenceManager.load(gameSetup, seed)
        if (save != null) return getGamePlaySession(save)

        val playData = GamePlayData()
        val game = coroutineScope { async { gameCreationManager.createGame(gameSetup) } }
        val solver = if (gameSetup.solver == GameSetup.Solver.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getSolver(gameSetup) } }
        }
        val evaluator = if (gameSetup.evaluator == GameSetup.Evaluator.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getEvaluator(gameSetup) } }
        }

        return ManagedGamePlaySession(seed, gameSetup, playData, game, solver, evaluator)
    }

    override suspend fun getGamePlaySession(gameSaveData: GameSaveData): GamePlaySession {
        val seed = gameSaveData.seed
        val setup = gameSaveData.setup
        val game = coroutineScope { async { gameCreationManager.getGame(gameSaveData) } }
        val solver = if (setup.solver == GameSetup.Solver.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getSolver(gameSaveData.setup) } }
        }
        val evaluator = if (setup.evaluator == GameSetup.Evaluator.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getEvaluator(gameSaveData.setup) } }
        }

        return ManagedGamePlaySession(seed, setup, gameSaveData.playData, game, solver, evaluator)
    }

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Modification
    //-----------------------------------------------------------------------------------------

    override suspend fun canUpdateGamePlaySession(session: GamePlaySession, update: GameSetup): Boolean {
        val settings = gameCreationManager.getSettings(update)
        return session is ManagedGamePlaySession && session.deferredGame.await().canUpdateSettings(settings)
    }

    @Throws(IllegalArgumentException::class)
    override suspend fun getUpdatedGamePlaySession(session: GamePlaySession, update: GameSetup): GamePlaySession {
        val settings = gameCreationManager.getSettings(update)
        if (session !is ManagedGamePlaySession) throw IllegalArgumentException("Provided session $session is not supported")
        if (!session.deferredGame.await().canUpdateSettings(settings)) throw IllegalArgumentException("Provided update $update not supported")

        // avoid invalidating the provided session by messing with its Game.
        // assume gameCreationManager uses caching to make subsequent recreation more efficient
        // than the initial.
        val save = session.getGameSaveData()
        val seed = save.seed
        val game = coroutineScope { async {
            val createdGame = gameCreationManager.getGame(save)
            createdGame.updateSettings(settings)
            createdGame
        } }
        val solver = if (update.solver == GameSetup.Solver.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getSolver(update) } }
        }
        val evaluator = if (update.evaluator == GameSetup.Evaluator.PLAYER) null else {
            coroutineScope { async { gameCreationManager.getEvaluator(update) } }
        }

        return ManagedGamePlaySession(seed, update, save.playData, game, solver, evaluator)
    }

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Managed GamePlaySession
    //-----------------------------------------------------------------------------------------

    private inner class ManagedGamePlaySession(
        override val seed: String?,
        override val gameSetup: GameSetup,
        override var gamePlayData: GamePlayData,
        val deferredGame: Deferred<Game>,
        val deferredSolver: Deferred<Solver>?,
        val deferredEvaluator: Deferred<Evaluator>?
    ): GamePlaySession {

        //region Game Data Accessors
        //-----------------------------------------------------------------------------------------

        override suspend fun getConstraints() = withGame { it.constraints }

        override suspend fun getCurrentGuess() = withGame { it.currentGuess }

        override suspend fun getCurrentMoves() = withGame { Pair(it.currentGuess, it.constraints) }

        override suspend fun getSettings() = withGame { it.settings }

        override suspend fun getGameSaveData() = withGame { GameSaveData(seed, gameSetup, it, gamePlayData) }

        override suspend fun getGameState() = withGame { it.state }
        override suspend fun getGameRound() = withGame { it.round }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Internal Game Data Access Helpers
        //-----------------------------------------------------------------------------------------
        val gameMutex = Mutex()

        suspend fun <T> withGame(action: (Game) -> T): T {
            val game = deferredGame.await()
            return gameMutex.withLock { action(game) }
        }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Persistence
        //-----------------------------------------------------------------------------------------

        override suspend fun save() {
            gamePersistenceManager.save(getGameSaveData())
        }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Game Play: User-Driven
        //-----------------------------------------------------------------------------------------

        @Throws(IllegalStateException::class, Game.IllegalGuessException::class)
        override suspend fun advanceWithGuess(candidate: String) {
            withGame { it.guess(candidate) }
        }

        @Throws(IllegalStateException::class, Game.IllegalEvaluationException::class)
        override suspend fun advanceWithEvaluation(constraint: Constraint) {
            withGame { it.evaluate(constraint) }
        }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Game Play: Session-Driven
        //-----------------------------------------------------------------------------------------

        override val canGenerateGuesses = deferredSolver != null
        override val canGenerateEvaluations = deferredEvaluator != null
        override val canGenerateSolutions = deferredEvaluator != null

        @Throws(IllegalStateException::class, Game.IllegalGuessException::class, UnsupportedOperationException::class)
        override suspend fun generateGuess(advance: Boolean): String {
            if (!canGenerateGuesses) {
                throw UnsupportedOperationException("Can't generate guesses with solver ${gameSetup.solver}")
            }

            // retrieve current constraints
            val constraints = getConstraints()

            // generate guess
            val solver = deferredSolver!!.await()
            val guess = withContext(computationDispatcher) { solver.generateGuess(constraints) }

            // TODO check that the Game's state has not changed since Constraint retrieval?
            // apply to the game (advance)
            if (advance) withGame { it.guess(guess) }

            return guess
        }

        @Throws(IllegalStateException::class, Game.IllegalEvaluationException::class, UnsupportedOperationException::class)
        override suspend fun generateEvaluation(advance: Boolean): Constraint {
            if (!canGenerateEvaluations) {
                throw UnsupportedOperationException("Can't generate constraints with evaluator ${gameSetup.evaluator}")
            }

            // retrieve current guess and constraints
            val (guess, constraints) = getCurrentMoves()
            if (guess == null) throw IllegalStateException("Game has no currentGuess")

            // generate Constraint
            val evaluator = deferredEvaluator!!.await()
            val constraint = withContext(computationDispatcher) { evaluator.evaluate(guess, constraints) }

            // TODO check that the Game's state has not changed since Constraint retrieval?
            // apply to the game (advance)
            if (advance) withGame { it.evaluate(constraint) }

            return constraint
        }

        @Throws(IllegalStateException::class, Game.IllegalGuessException::class, UnsupportedOperationException::class)
        override suspend fun generateSolution(advance: Boolean): String {
            if (!canGenerateSolutions) {
                throw UnsupportedOperationException("Can't generate solutions with evaluator ${gameSetup.evaluator}")
            }

            // retrieve current constraints
            val constraints = getConstraints()

            // generate solution
            val evaluator = deferredEvaluator!!.await()
            val solution = withContext(computationDispatcher) { evaluator.peek(constraints) }

            // TODO check that the Game's state has not changed since Constraint retrieval?
            // apply to the game (advance)
            if (advance) withGame { it.guess(solution) }

            return solution
        }

        //-----------------------------------------------------------------------------------------
        //endregion

    }
    //-----------------------------------------------------------------------------------------
    //endregion

}