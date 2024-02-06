package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.*
import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.setup.GameSetupManager
import com.peaceray.codeword.data.manager.record.GameRecordManager
import com.peaceray.codeword.presentation.contracts.GameOutcomeContract
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

class GameOutcomePresenter @Inject constructor(): GameOutcomeContract.Presenter, BasePresenter<GameOutcomeContract.View>() {

    //region Fields and View Attachment
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gameCreationManager: GameCreationManager
    @Inject lateinit var gameRecordManager: GameRecordManager

    lateinit var uuid: UUID
    var outcome: GameOutcome? = null

    override fun onAttached() {
        super.onAttached()

        uuid = view!!.getGameUUID()

        // set placeholders for immediate UI layout
        setPlaceholders()

        // load the actual game outcome (this call chains into loading the history)
        viewScope.launch { loadOutcome(uuid) }
    }

    override fun onDetached() {
        super.onDetached()
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region View Configuration and DB Access
    //---------------------------------------------------------------------------------------------

    private fun setPlaceholders() {
        val seed = view?.getGameSeed()
        val setup = view?.getGameSetup()
        setPlaceholderOutcome(seed, setup)
        setPlaceholderHistory(seed, setup)
    }

    private fun setPlaceholderOutcome(seed: String?, gameSetup: GameSetup?) {
        if (gameSetup != null) {
            view?.setGameOutcome(GameOutcome(
                uuid,
                gameSetupManager.getType(gameSetup),
                gameSetup.daily,
                gameSetupManager.isHard(gameSetup),
                gameSetup.solver,
                gameSetup.evaluator,
                seed,
                GameOutcome.Outcome.LOADING,
                0,
                listOf(),
                null,
                null,
                gameCreationManager.getSettings(gameSetup).rounds,
                Date()
                ))
        }
    }

    private fun setPlaceholderHistory(seed: String?, gameSetup: GameSetup?) {
        if (gameSetup != null) {
            val type = gameSetupManager.getType(gameSetup)
            val daily = gameSetup.daily
            view?.setGameHistory(
                GameTypePerformanceRecord(type, daily),
                TotalPerformanceRecord(daily),
                GameTypePlayerStreak(type)
            )
        }
    }

    private suspend fun loadOutcome(uuid: UUID) {
        val recordedOutcome = gameRecordManager.getOutcome(uuid)
        if (recordedOutcome != null) {
            outcome = recordedOutcome
            view?.setGameOutcome(recordedOutcome)
            loadHistory(recordedOutcome)
        } else {
            Timber.e("Not able to load game outcome")
            view?.close()
        }
    }

    private suspend fun loadHistory(outcome: GameOutcome) {
        val performance = gameRecordManager.getPerformance(outcome, outcome.daily)
        val totalPerformance = gameRecordManager.getTotalPerformance(outcome.solver, outcome.evaluator)
        val streak = gameRecordManager.getPlayerStreak(outcome)

        view?.setGameHistory(performance, totalPerformance, streak)
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region User Interaction
    //---------------------------------------------------------------------------------------------

    override fun onShareButtonClicked() {
        outcome?.let { view?.share(it) }
    }

    override fun onCopyButtonClicked() {
        outcome?.let{ view?.copy(it) }
    }

    override fun onCloseButtonClicked() {
        // no need to dispose observables as that happens in [onDetached]
        view?.close()
    }

    //---------------------------------------------------------------------------------------------
    //endregion
}