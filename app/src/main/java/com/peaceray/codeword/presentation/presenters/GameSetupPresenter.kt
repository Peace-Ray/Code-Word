package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameDefaultsManager
import com.peaceray.codeword.domain.manager.game.GameSessionManager
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

// TODO formalize this
private val ROUNDS_RANGE = 0..8

private const val DAILY_DEFAULTS_KEY = "GameSetupPresenter.Daily"
private const val SEEDED_DEFAULTS_KEY = "GameSetupPresenter.Seeded"
private const val CUSTOM_DEFAULTS_KEY = "GameSetupPresenter.Custom"

class GameSetupPresenter @Inject constructor(): GameSetupContract.Presenter, BasePresenter<GameSetupContract.View>() {

    //region Fields and View Attachment
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gameSessionManager: GameSessionManager
    @Inject lateinit var gameDefaultsManager: GameDefaultsManager

    // game setup fields
    private lateinit var type: GameSetupContract.Type
    private lateinit var gameSetup: GameSetup
    private var seed: String? = null
    private var game: Game? = null

    // RxJava disposable
    private var disposable: Disposable = Disposable.disposed()

    override fun onAttached() {
        super.onAttached()

        // read the type and configure data
        updateTypeAndSetup(view!!.getType())

        // configure view
        updateFeatureSupport()
        updateFeatureRanges()
        updateViewSetup()
    }

    override fun onDetached() {
        super.onDetached()

        // TODO persist current setup (maybe unless canceled?)
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Type, Feature, Progress Support
    //---------------------------------------------------------------------------------------------
    private fun updateTypeAndSetup(type: GameSetupContract.Type) {
        // set type
        this.type = type

        // load GameSaveData
        val seedAndSetup = view?.getOngoingGameSetup()

        // load game setup
        gameSetup = try {
            when(type) {
                GameSetupContract.Type.DAILY -> {
                    val hardMode = gameDefaultsManager.hardMode
                    gameSetupManager.getDailySetup(hard = hardMode)
                }
                GameSetupContract.Type.SEEDED -> {
                    val baseSetup = gameSetupManager.getSetup(gameDefaultsManager.hardMode)
                    gameSetupManager.modifyGameSetup(
                        baseSetup,
                        board = gameDefaultsManager.board,
                        vocabulary = gameDefaultsManager.vocabulary
                    )
                }
                GameSetupContract.Type.CUSTOM -> {
                    val hard = gameDefaultsManager.getHardMode(CUSTOM_DEFAULTS_KEY)
                    val defaults = gameDefaultsManager.get(CUSTOM_DEFAULTS_KEY)
                    val baseSetup = gameSetupManager.getSetup(hard)
                    gameSetupManager.modifyGameSetup(
                        baseSetup,
                        board = defaults.board,
                        evaluation = defaults.evaluation,
                        vocabulary = defaults.vocabulary,
                        solver = defaults.solver,
                        evaluator = defaults.evaluator
                    )
                }
                GameSetupContract.Type.ONGOING -> {
                    seedAndSetup!!.second
                }
            }
        } catch (err: Exception) {
            Timber.e(err, "Couldn't create appropriate GameSetup for $type using persisted settings")
            if (type == GameSetupContract.Type.DAILY) {
                gameSetupManager.getDailySetup()
            } else {
                gameSetupManager.getSetup()
            }
        }

        // load game
        game = if (seedAndSetup == null) null else gameSessionManager.getGame(seedAndSetup.first, seedAndSetup.second, false)

        seed = gameSetupManager.getSeed(gameSetup)
    }

    private fun updateFeatureSupport() {
        // all types allow these features
        val features = mutableListOf<GameSetupContract.Feature>()

        // TODO only include LAUNCH if actually available; e.g. for DAILY, check if not yet complete
        features.add(GameSetupContract.Feature.LAUNCH)

        // hard mode is available (toggleable) if the game language supports it, AND this is
        // either a new game (no moves or no "game" object) or a hard one, in which case "hard"
        // can be disabled. It cannot be re-enabled for an in-progress game.
        if (
            gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language).hardModeSupported &&
            (game == null || !game!!.started || gameSetupManager.isHard(gameSetup))
        ) {
            features.add(GameSetupContract.Feature.HARD_MODE)
        }

        // number of rounds can always be changed for non-cheater games (but only to at least
        // as many as have been played, though that is set in [updateFeatureRanges].
        if (gameSetup.evaluator != GameSetup.Evaluator.CHEATER) {
            features.add(GameSetupContract.Feature.ROUNDS)
        }

        // language can only be altered for new, non-daily games.
        if (type != GameSetupContract.Type.DAILY && type != GameSetupContract.Type.ONGOING) {
            // allow language editing
            features.add(GameSetupContract.Feature.CODE_LANGUAGE)
            features.add(GameSetupContract.Feature.CODE_LENGTH)
            if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.ENUMERATED) {
                features.add(GameSetupContract.Feature.CODE_CHARACTERS)
            }
        }

        // Seeded games: can edit the seed. Custom games have player role options.
        when (type) {
            GameSetupContract.Type.DAILY, GameSetupContract.Type.ONGOING -> {}
            GameSetupContract.Type.SEEDED -> features.add(GameSetupContract.Feature.SEED)
            GameSetupContract.Type.CUSTOM -> {
                features.add(GameSetupContract.Feature.PLAYER_ROLE)
                if (gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
                    features.add(GameSetupContract.Feature.EVALUATOR_HONEST)
                }
            }
        }

        view?.setFeatureAllowed(features)
    }

    private fun updateFeatureRanges() {
        val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
        view?.setFeatureValuesAvailable(GameSetupContract.Feature.CODE_LENGTH, languageDetails.codeLengthsSupported)
        view?.setFeatureValuesAvailable(GameSetupContract.Feature.CODE_CHARACTERS, languageDetails.codeCharactersSupported)

        // TODO recommend number of rounds based on vocabulary
        val round = game?.round ?: 1
        view?.setFeatureValuesAvailable(
            GameSetupContract.Feature.ROUNDS,
            ROUNDS_RANGE.toList().filter { it == 0 || it >= round }
        )
    }

    private fun updateViewSetup() {
        // CUSTOM games are effectively seedless and can always be repeated
        // DAILY and SEEDED games can only be attempted once
        val seed = this.seed
        val gameSetup = this.gameSetup
        disposable.dispose()
        disposable = computeSessionProgress(seed)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { progress ->
                    if (seed == this.seed && gameSetup == this.gameSetup) {
                        val canLaunch = if (type == GameSetupContract.Type.ONGOING) {
                            progress == GameSetupContract.SessionProgress.ONGOING
                        } else {
                            progress == GameSetupContract.SessionProgress.NEW
                        }
                        view?.setGameSetup(seed, gameSetup, progress)
                        view?.setFeatureAllowed(GameSetupContract.Feature.LAUNCH, canLaunch)
                    } else {
                        Timber.w("Would have set progress to $progress but gameSetup has changed")
                    }
                },
                { error ->
                    Timber.e(error, "An error occurred loading game session progress")
                    if (seed == this.seed && gameSetup == this.gameSetup) {
                        view?.setGameSetup(seed, gameSetup, GameSetupContract.SessionProgress.NEW)
                        view?.setFeatureAllowed(GameSetupContract.Feature.LAUNCH, true)
                    }
                }
            )
    }

    private fun computeSessionProgress(seed: String?): Single<GameSetupContract.SessionProgress> {
        return if (seed == null) Single.just(GameSetupContract.SessionProgress.NEW) else {
            Single.defer {
                val progress = when(gameSessionManager.loadState(seed)) {
                    Game.State.GUESSING, Game.State.EVALUATING -> GameSetupContract.SessionProgress.ONGOING
                    Game.State.WON -> GameSetupContract.SessionProgress.WON
                    Game.State.LOST -> GameSetupContract.SessionProgress.LOST
                    else -> GameSetupContract.SessionProgress.NEW
                }
                Single.just(progress)
            }.subscribeOn(Schedulers.io())
        }
    }

    private fun performLaunch(seed: String?, gameSetup: GameSetup) {
        // persist settings for this type
        val hardMode = gameSetup.evaluation.enforced != ConstraintPolicy.IGNORE
        when(type) {
            GameSetupContract.Type.DAILY -> {
                gameDefaultsManager.hardMode = hardMode
                gameDefaultsManager.put(DAILY_DEFAULTS_KEY, gameSetup)
            }
            GameSetupContract.Type.SEEDED -> {
                gameDefaultsManager.put(gameSetup)
                gameDefaultsManager.put(SEEDED_DEFAULTS_KEY, gameSetup)
            }
            GameSetupContract.Type.CUSTOM -> {
                if (gameSetup.solver == GameSetup.Solver.PLAYER) {
                    gameDefaultsManager.hardMode = hardMode
                    gameDefaultsManager.put(CUSTOM_DEFAULTS_KEY, gameSetup)
                }
            }
        }

        // tell view for navigation
        view?.finishGameSetup(seed, gameSetup)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region View UI
    //---------------------------------------------------------------------------------------------
    override fun onTypeSelected(type: GameSetupContract.Type) {
        if (this.type != type) {
            updateTypeAndSetup(type)

            // configure view
            updateFeatureSupport()
            updateFeatureRanges()
            updateViewSetup()
        }
    }

    override fun onLaunchButtonClicked() {
        val seed = this.seed
        val gameSetup = this.gameSetup

        disposable.dispose()
        disposable = computeSessionProgress(seed)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { progress ->
                    val canLaunch = if (type == GameSetupContract.Type.ONGOING) {
                        progress == GameSetupContract.SessionProgress.ONGOING
                    } else {
                        progress == GameSetupContract.SessionProgress.NEW
                    }

                    if (canLaunch) performLaunch(seed, gameSetup) else {
                        view?.showError(GameSetupContract.Feature.LAUNCH, GameSetupContract.Error.NOT_ALLOWED)
                    }
                },
                { error ->
                    Timber.e(error, "An error occurred checking game progress")
                    performLaunch(seed, gameSetup)
                }
            )
    }

    override fun onCancelButtonClicked() {
        view?.cancelGameSetup()
    }

    override fun onSeedEntered(seed: String): Boolean {
        if (type == GameSetupContract.Type.SEEDED) {
            try {
                val hard = gameSetupManager.isHard(gameSetup)
                gameSetup = gameSetupManager.getSetup(seed, hard)
                this.seed = gameSetupManager.getSeed(gameSetup)

                // update everything for the view
                updateFeatureSupport()
                updateFeatureRanges()
                updateViewSetup()
                return true
            } catch (err: Exception) {
                Timber.w(err, "Bad seed $seed entered")
                view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.INVALID)
            }
        } else {
            Timber.w("Seed entered for non-seed type $type")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.NOT_ALLOWED)
        }

        // change back!
        return false
    }

    override fun onRolesEntered(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): Boolean {
        if (type == GameSetupContract.Type.CUSTOM) {
            gameSetup = gameSetupManager.modifyGameSetup(gameSetup, solver = solver, evaluator = evaluator)
            this.seed = gameSetupManager.getSeed(gameSetup)

            // all existing features remain the same except whether "Honest Opponent" is available
            view?.setFeatureAllowed(GameSetupContract.Feature.EVALUATOR_HONEST, evaluator != GameSetup.Evaluator.PLAYER)
            updateViewSetup()
            return true
        } else {
            Timber.w("Player roles entered for non-seed type $type")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.NOT_ALLOWED)
        }

        // change back!
        return false
    }

    override fun onLanguageEntered(language: CodeLanguage): Boolean {
        if (type != GameSetupContract.Type.DAILY) {
            if (language != gameSetup.vocabulary.language) {
                // update the vocabulary using language defaults
                gameSetup = gameSetupManager.modifyGameSetup(gameSetup, language = language)
                seed = gameSetupManager.getSeed(gameSetup)

                // character length is sometimes configurable
                view?.setFeatureAllowed(
                    GameSetupContract.Feature.CODE_CHARACTERS,
                    gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.ENUMERATED
                )

                // set allowed character sets and word lengths
                val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
                view?.setFeatureValuesAvailable(GameSetupContract.Feature.CODE_LENGTH, languageDetails.codeLengthsSupported)
                view?.setFeatureValuesAvailable(GameSetupContract.Feature.CODE_CHARACTERS, languageDetails.codeCharactersSupported)

                updateViewSetup()
                return true
            }
        } else {
            Timber.w("Language entered for Daily type $type")
            view?.showError(GameSetupContract.Feature.CODE_LANGUAGE, GameSetupContract.Error.NOT_ALLOWED)
        }

        return false
    }

    override fun onFeatureEntered(feature: GameSetupContract.Feature, active: Boolean): Boolean {
        // Boolean features: EVALUATOR_HONEST, HARD_MODE
        when (feature) {
            GameSetupContract.Feature.HARD_MODE -> {
                // allowed in all contexts
                gameSetup = gameSetupManager.modifyGameSetup(gameSetup, hard = active)
                seed = gameSetupManager.getSeed(gameSetup)

                updateViewSetup()
                return true
            }
            GameSetupContract.Feature.EVALUATOR_HONEST -> {
                // allowed only when CUSTOM and non-human evaluator
                if (type == GameSetupContract.Type.CUSTOM && gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
                    gameSetup = gameSetupManager.modifyGameSetup(
                        gameSetup,
                        evaluator = if (active) GameSetup.Evaluator.HONEST else GameSetup.Evaluator.CHEATER
                    )
                    seed = gameSetupManager.getSeed(gameSetup)

                    updateViewSetup()
                    return true
                } else {
                    view?.showError(feature, GameSetupContract.Error.NOT_ALLOWED)
                }
            }
            else -> view?.showError(feature, GameSetupContract.Error.INVALID)
        }

        return false
    }

    override fun onFeatureEntered(feature: GameSetupContract.Feature, value: Int): Boolean {
        // Int features: CODE_LENGTH, CODE_CHARACTERS, ROUNDS
        val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
        var revisedSetup: GameSetup? = null
        when (feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                if (value in languageDetails.codeLengthsSupported) {
                    revisedSetup = gameSetupManager.modifyGameSetup(
                        gameSetup,
                        vocabulary = GameSetup.Vocabulary(
                            gameSetup.vocabulary.language,
                            gameSetup.vocabulary.type,
                            value,
                            gameSetup.vocabulary.characters
                        )
                    )
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED)
                }
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                if (value in languageDetails.codeCharactersSupported) {
                    revisedSetup = gameSetupManager.modifyGameSetup(
                        gameSetup,
                        vocabulary = GameSetup.Vocabulary(
                            gameSetup.vocabulary.language,
                            gameSetup.vocabulary.type,
                            gameSetup.vocabulary.length,
                            value
                        )
                    )
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED)
                }
            }
            GameSetupContract.Feature.ROUNDS -> {
                if (value in ROUNDS_RANGE) {
                    revisedSetup = gameSetupManager.modifyGameSetup(
                        gameSetup,
                        board = GameSetup.Board(value)
                    )
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED)
                }
            }
            else -> view?.showError(feature, GameSetupContract.Error.INVALID)
        }

        if (revisedSetup != null) {
            gameSetup = revisedSetup
            seed = gameSetupManager.getSeed(gameSetup)
            updateViewSetup()
            return true
        }

        return false
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}