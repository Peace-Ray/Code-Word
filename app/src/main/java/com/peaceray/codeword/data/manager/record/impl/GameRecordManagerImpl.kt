package com.peaceray.codeword.data.manager.record.impl

import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.*
import com.peaceray.codeword.data.manager.game.setup.GameSetupManager
import com.peaceray.codeword.data.manager.record.GameRecordManager
import com.peaceray.codeword.data.model.game.save.GamePlayData
import com.peaceray.codeword.data.source.CodeWordDb
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.glue.ForLocalIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * An implementation of GameRecordManager that relies on an injected CodeWordDb data source.
 */
@Singleton
class GameRecordManagerImpl @Inject constructor(
    val gameSetupManager: GameSetupManager,
    @ForLocalIO val ioDispatcher: CoroutineDispatcher,
    val codeWordDb: CodeWordDb
): GameRecordManager {

    //region GameRecordManager
    //---------------------------------------------------------------------------------------------
    private val recordMutex = Mutex()

    override suspend fun record(seed: String?, setup: GameSetup, game: Game, gamePlayData: GamePlayData, secret: String?) {
        record(
            GameOutcome(
                uuid = game.uuid,
                type = gameSetupManager.getType(setup),
                daily = setup.daily,
                hard = gameSetupManager.isHard(setup),
                solver = setup.solver,
                evaluator = setup.evaluator,
                seed = seed,
                outcome = when {
                    game.won -> GameOutcome.Outcome.WON
                    game.lost -> GameOutcome.Outcome.LOST
                    else -> GameOutcome.Outcome.FORFEIT
                },
                round = game.round,
                hintingSinceRound = if (gamePlayData.hintingEver) gamePlayData.hintingSinceRound else -1,
                constraints = game.constraints,
                guess = game.currentGuess,
                secret = secret,
                rounds = game.settings.rounds,
                recordedAt = Date()
            )
        )
    }

    override suspend fun record(gameSaveData: GameSaveData, secret: String?) {
        record(
            GameOutcome(
                uuid = gameSaveData.uuid,
                type = gameSetupManager.getType(gameSaveData.setup),
                daily = gameSaveData.setup.daily,
                hard = gameSetupManager.isHard(gameSaveData.setup),
                solver = gameSaveData.setup.solver,
                evaluator = gameSaveData.setup.evaluator,
                seed = gameSaveData.seed,
                outcome = when {
                    gameSaveData.won -> GameOutcome.Outcome.WON
                    gameSaveData.lost -> GameOutcome.Outcome.LOST
                    else -> GameOutcome.Outcome.FORFEIT
                },
                round = gameSaveData.round,
                hintingSinceRound = if (gameSaveData.playData.hintingEver) gameSaveData.playData.hintingSinceRound else -1,
                constraints = gameSaveData.constraints,
                guess = gameSaveData.currentGuess,
                secret = secret,
                rounds = gameSaveData.settings.rounds,
                recordedAt = Date()
            )
        )
    }

    private suspend fun record(outcome: GameOutcome) {
        Timber.d("record")
        withContext(ioDispatcher) {
            recordMutex.withLock {
                // retrieve the previous outcome (if any), and write the update
                val previousOutcome = codeWordDb.getOutcome(outcome.uuid)
                codeWordDb.putOutcome(outcome)

                // record player streak ONLY if the player guessed and their opponent was honest,
                // and only once per game (unless transforming a win into a loss automagically...?)
                if (
                    outcome.solver == GameSetup.Solver.PLAYER &&
                    outcome.evaluator == GameSetup.Evaluator.HONEST &&
                    (previousOutcome == null || outcome.outcome == GameOutcome.Outcome.LOST)
                ) {
                    codeWordDb.updatePlayerStreak(outcome.type, outcome.daily, outcome.outcome)
                }

                // update performance record, subtracting the previous outcome and adding the new one
                codeWordDb.updatePerformanceRecords(
                    outcome.type,
                    outcome.solver,
                    outcome.evaluator,
                    outcome.daily,
                    add = listOf(outcome).map { Pair(it.outcome, it.round) },
                    remove = listOfNotNull(previousOutcome).map { Pair(it.outcome, it.round) }
                )
            }
        }
    }

    override suspend fun hasOutcome(uuid: UUID): Boolean {
        return withContext(ioDispatcher) {
            codeWordDb.hasOutcome(uuid)
        }
    }


    override suspend fun getOutcome(uuid: UUID): GameOutcome? {
        return withContext(ioDispatcher) {
            codeWordDb.getOutcome(uuid)
        }
    }

    override suspend fun getTotalPerformance(
        solver: GameSetup.Solver,
        evaluator: GameSetup.Evaluator
    ): TotalPerformanceRecord {
        return withContext(ioDispatcher) {
            codeWordDb.getTotalPerformanceRecord(solver, evaluator)
        }
    }

    override suspend fun getPerformance(outcome: GameOutcome, strict: Boolean): GameTypePerformanceRecord {
        return withContext(ioDispatcher) {
            codeWordDb.getGameTypePerformanceRecord(
                outcome.type,
                outcome.solver,
                outcome.evaluator,
                if (strict) outcome.daily else null
            )
        }
    }

    override suspend fun getPerformance(setup: GameSetup, strict: Boolean): GameTypePerformanceRecord {
        return withContext(ioDispatcher) {
            codeWordDb.getGameTypePerformanceRecord(
                gameSetupManager.getType(setup),
                setup.solver,
                setup.evaluator,
                if (strict) setup.daily else null
            )
        }
    }

    override suspend fun getPlayerStreak(outcome: GameOutcome): GameTypePlayerStreak {
        return getPlayerStreak(outcome.type, outcome.solver, outcome.evaluator)
    }

    override suspend fun getPlayerStreak(setup: GameSetup): GameTypePlayerStreak {
        val gameType = gameSetupManager.getType(setup)
        return getPlayerStreak(gameType, setup.solver, setup.evaluator)
    }

    private suspend fun getPlayerStreak(gameType: GameType, solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): GameTypePlayerStreak {
        return if (solver == GameSetup.Solver.PLAYER && evaluator == GameSetup.Evaluator.HONEST) {
            withContext(ioDispatcher) {
                codeWordDb.getPlayerStreak(gameType)
            }
        } else GameTypePlayerStreak(gameType)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}