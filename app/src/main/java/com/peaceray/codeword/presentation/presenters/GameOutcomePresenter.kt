package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.*
import com.peaceray.codeword.domain.manager.game.creation.GameCreationManager
import com.peaceray.codeword.domain.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.domain.manager.game.setup.GameSetupManager
import com.peaceray.codeword.domain.manager.record.GameRecordManager
import com.peaceray.codeword.presentation.contracts.GameOutcomeContract
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
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

    var disposable: Disposable = Disposable.disposed()

    override fun onAttached() {
        super.onAttached()

        uuid = view!!.getGameUUID()

        // set placeholders for immediate UI layout
        setPlaceholders()

        // load the actual game outcome (this call chains into loading the history)
        loadOutcome(uuid)
    }

    override fun onDetached() {
        super.onDetached()

        disposable.dispose()
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
            view?.setGameHistory(
                GameTypePerformanceRecord(type),
                TotalPerformanceRecord(),
                GameTypePlayerStreak(type)
            )
        }
    }

    private fun loadOutcome(uuid: UUID) {
        disposable = Single.defer { Single.just(gameRecordManager.getOutcome(uuid)!!) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    outcome = it
                    view?.setGameOutcome(it)
                    loadHistory(it)
                },
                {
                    Timber.e(it, "Not able to load game outcome")
                    view?.close()
                }
            )
    }

    private fun loadHistory(outcome: GameOutcome) {
        disposable = Single.defer { Single.just(Triple(
            gameRecordManager.getPerformance(outcome, outcome.daily),   // strict if daily, otherwise include both types
            gameRecordManager.getTotalPerformance(outcome.solver, outcome.evaluator),
            gameRecordManager.getPlayerStreak(outcome)
        )) }.subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { view?.setGameHistory(it.first, it.second, it.third) },
                { Timber.e(it, "Not able to load game history") }
            )
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