package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.creation.GameCreationManager
import com.peaceray.codeword.domain.manager.game.defaults.GameDefaultsManager
import com.peaceray.codeword.domain.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.domain.manager.game.setup.GameSetupManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

private const val DAILY_DEFAULTS_KEY = "GameSetupPresenter.Daily"
private const val SEEDED_DEFAULTS_KEY = "GameSetupPresenter.Seeded"
private const val CUSTOM_DEFAULTS_KEY = "GameSetupPresenter.Custom"

class GameSetupPresenter @Inject constructor(): GameSetupContract.Presenter, BasePresenter<GameSetupContract.View>() {

    //region Fields and View Attachment
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gameCreationManager: GameCreationManager
    @Inject lateinit var gamePersistenceManager: GamePersistenceManager
    @Inject lateinit var gameDefaultsManager: GameDefaultsManager

    // game setup fields
    private lateinit var type: GameSetupContract.Type
    private lateinit var qualifiers: Set<GameSetupContract.Qualifier>
    private var seed: String? = null
    private lateinit var gameSetup: GameSetup
    private lateinit var review: GameStatusReview
    private var game: Game? = null

    // Persisted View state
    private var savedViewState: SavedViewState? = null

    override fun onAttached() {
        super.onAttached()

        if (savedViewState?.matchesState(view!!) == true) {
            Timber.v("Restoring saved view state from $savedViewState")
            updateFeatureSupport()
            updateFeatureRanges()
            updateViewSetup(this.review)
        } else {
            this.type = view!!.getType()
            this.qualifiers = view!!.getQualifiers()
            viewScope.launch {
                Timber.v("Constructing fresh view state; ignoring $savedViewState")
                // read the type and configure data
                updateTypeAndSetup(type, qualifiers)

                // configure view and (asynchronously) load status
                updateGameSetupAndView(seed, gameSetup)
            }
        }

        savedViewState = null
    }

    override fun onDetached() {
        super.onDetached()

        savedViewState = SavedViewState(view!!.getType(), view!!.getQualifiers(), view!!.getOngoingGameSetup())
    }

    private data class SavedViewState(
        val type: GameSetupContract.Type,
        val qualifiers: Set<GameSetupContract.Qualifier>,
        val ongoingSetup: Pair<String?, GameSetup>?
    ) {
        fun matchesState(view: GameSetupContract.View): Boolean {
            return type == view.getType() &&
                    qualifiers.intersect(view.getQualifiers()).size == qualifiers.size &&
                    ongoingSetup == view.getOngoingGameSetup()
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Type, Feature, Progress Support
    //---------------------------------------------------------------------------------------------
    private suspend fun updateTypeAndSetup(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>) {
        // set type
        this.type = type
        this.qualifiers = qualifiers

        // get view-held information
        val seedAndSetup = view?.getOngoingGameSetup()

        // initial game setup (possibly overwritten later)
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
                        vocabulary = gameDefaultsManager.vocabulary,
                        evaluation = gameDefaultsManager.evaluation
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

        Timber.d("Daily has setup $gameSetup")

        seed = gameSetupManager.getSeed(gameSetup)

        // load game
        game = if (seedAndSetup != null) {
            gameCreationManager.getGame(seedAndSetup.first, seedAndSetup.second)
        } else if (seed != null && (type == GameSetupContract.Type.DAILY || type == GameSetupContract.Type.SEEDED)) {
            val loadedSave = gamePersistenceManager.load(seed, null)
            if (loadedSave != null) {
                // replace existing setup
                gameSetup = loadedSave.setup

                Timber.d("Updated setup from session manager to $gameSetup")
            }
            loadedSave?.let { gameCreationManager.getGame(it) }
        } else {
            null
        }

        // update code composition
        when (gameSetup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> {
                view?.setCodeLanguage(
                    gameCreationManager.getCodeCharacters(gameSetup),
                    gameSetup.vocabulary.language.locale!!
                )
            }
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> {
                view?.setCodeComposition(gameCreationManager.getCodeCharacters(gameSetup))
            }
        }
    }

    private fun updateGameSetupAndView(gameSetup: GameSetup) {
        updateGameSetupAndView(gameSetupManager.getSeed(gameSetup), gameSetup)
    }

    private fun updateGameSetupAndView(seed: String?, gameSetup: GameSetup) {
        // update properties wth a LOADING placeholder status
        this.seed = seed
        this.gameSetup = gameSetup
        this.review = createGameStatusReview(this.type, seed, gameSetup, GameStatusReview.Status.LOADING)

        // Update everything for the view. Hold off on a LOADING update
        updateFeatureSupport()
        updateFeatureRanges()
        updateViewSetup(this.review)

        // replace placeholder with actual status
        viewScope.launch {
            val progress = try {
                getSessionProgress(seed)
            } catch (error: Exception) {
                // TODO catch only specific exceptions?
                Timber.e(error, "An error occurred loading game session progress")
                GameStatusReview.Status.NEW
            }

            if (seed == this@GameSetupPresenter.seed && gameSetup == this@GameSetupPresenter.gameSetup) {
                review = createGameStatusReview(type, seed, gameSetup, progress)
                if (progress != GameStatusReview.Status.NEW) {
                    updateFeatureSupport()
                    updateFeatureRanges()
                }
                updateViewSetup(review)
            } else {
                Timber.w("Would have set progress to $progress but gameSetup has changed")
            }
        }
    }

    private fun updateFeatureSupport() {
        // some metadata
        val langDeets = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
        val roundRec = gameSetupManager.getRecommendedRounds(gameSetup.vocabulary, gameSetup.evaluation)

        val availabilityMap: MutableMap<GameSetupContract.Feature, GameSetupContract.Availability> = mutableMapOf()
        val qualifierMap: MutableMap<GameSetupContract.Feature, GameSetupContract.Qualifier> = mutableMapOf()

        // only include LAUNCH if actually available; e.g. for DAILY, check if not yet complete
        val canLaunch: Pair<Boolean, GameSetupContract.Qualifier?> = isLaunchAllowed(type, qualifiers, review)
        val canLaunchQualifier = canLaunch.second
        availabilityMap[GameSetupContract.Feature.LAUNCH] = if (canLaunch.first) {
            GameSetupContract.Availability.AVAILABLE
        } else {
            GameSetupContract.Availability.LOCKED
        }
        if (canLaunchQualifier != null) qualifierMap[GameSetupContract.Feature.LAUNCH] = canLaunchQualifier

        // hard mode is disabled for some game types. For the rest, it should be locked if the game
        // is in-progress and not already set to hard (can be disabled, but not enabled).
        availabilityMap[GameSetupContract.Feature.HARD_MODE] = when {
            langDeets.hardModeConstraint[gameSetup.evaluation.type] == null -> GameSetupContract.Availability.DISABLED
            game != null && game!!.started && (!gameSetupManager.isHard(gameSetup) || game!!.over) -> GameSetupContract.Availability.LOCKED
            else -> GameSetupContract.Availability.AVAILABLE
        }

        // Number of rounds. Locked for dailies, and games with only one valid
        // rounds settings (e.g. games already in progress).
        availabilityMap[GameSetupContract.Feature.ROUNDS] = if (
            (game != null && game!!.over)
            || gameSetup.daily
            || ((game?.round ?: 1)..(roundRec.second)).toList().size <= 1
        ) GameSetupContract.Availability.LOCKED else GameSetupContract.Availability.AVAILABLE

        // language: locked for in-progress, disabled for dailies
        val languageAvailability = when {
            type == GameSetupContract.Type.DAILY || gameSetup.daily -> GameSetupContract.Availability.DISABLED
            type == GameSetupContract.Type.ONGOING || game != null -> GameSetupContract.Availability.LOCKED
            else -> GameSetupContract.Availability.AVAILABLE
        }
        availabilityMap[GameSetupContract.Feature.CODE_LENGTH] = languageAvailability
        availabilityMap[GameSetupContract.Feature.CODE_LANGUAGE] = languageAvailability
        availabilityMap[GameSetupContract.Feature.CODE_CHARACTERS] = if (langDeets.isEnumeration) {
            languageAvailability
        } else {
            GameSetupContract.Availability.DISABLED
        }
        availabilityMap[GameSetupContract.Feature.CODE_CHARACTER_REPETITION] = when (languageAvailability) {
            GameSetupContract.Availability.AVAILABLE -> {
                if (gameSetup.vocabulary.length <= gameSetup.vocabulary.characters) {
                    GameSetupContract.Availability.AVAILABLE
                } else {
                    GameSetupContract.Availability.LOCKED
                }
            }
            else -> languageAvailability
        }
        availabilityMap[GameSetupContract.Feature.CODE_EVALUATION_POLICY] = if (langDeets.evaluationsSupported.size > 1) {
            languageAvailability
        } else {
            GameSetupContract.Availability.LOCKED
        }

        availabilityMap[GameSetupContract.Feature.SEED] = when(type) {
            GameSetupContract.Type.DAILY -> GameSetupContract.Availability.LOCKED
            GameSetupContract.Type.SEEDED -> GameSetupContract.Availability.AVAILABLE
            GameSetupContract.Type.CUSTOM -> GameSetupContract.Availability.DISABLED
            else -> {
                if (gameSetup.solver == GameSetup.Solver.PLAYER && gameSetup.evaluator == GameSetup.Evaluator.HONEST) {
                    GameSetupContract.Availability.LOCKED
                } else {
                    GameSetupContract.Availability.DISABLED
                }
            }
        }

        if (type == GameSetupContract.Type.CUSTOM) {
            // TODO when player role is implemented, allow this
            // features[GameSetupContract.Feature.PLAYER_ROLE] = GameSetupContract.Availability.AVAILABLE

            if (gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
                availabilityMap[GameSetupContract.Feature.EVALUATOR_HONEST] = GameSetupContract.Availability.AVAILABLE
            }
        }

        // anything unspecified is DISABLED.
        view?.setFeatureAvailability(availabilityMap, qualifierMap)
    }

    private fun updateFeatureRanges() {
        // available languages
        view?.setLanguagesAllowed(listOf(
            CodeLanguage.ENGLISH,
            CodeLanguage.CODE
        ))

        val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
        view?.setFeatureValuesAllowed(GameSetupContract.Feature.CODE_LENGTH, languageDetails.codeLengthsSupported)
        view?.setFeatureValuesAllowed(GameSetupContract.Feature.CODE_CHARACTERS, languageDetails.codeCharactersSupported)
        view?.setEvaluationPoliciesAllowed(languageDetails.evaluationsSupported)

        // TODO recommend number of rounds based on vocabulary
        val round = game?.round ?: 1
        val roundRec = gameSetupManager.getRecommendedRounds(gameSetup.vocabulary, gameSetup.evaluation)
        view?.setFeatureValuesAllowed(
            GameSetupContract.Feature.ROUNDS,
            listOf(0) + (round..(roundRec.second)).toList()
        )
    }

    private fun updateViewSetup(review: GameStatusReview) {
        val canLaunch = isLaunchAllowed(type, qualifiers, review)
        view?.setGameStatusReview(review)
        view?.setFeatureAvailability(
            GameSetupContract.Feature.LAUNCH,
            if (canLaunch.first) GameSetupContract.Availability.AVAILABLE else GameSetupContract.Availability.LOCKED,
            canLaunch.second
        )
    }

    private suspend fun getSessionProgress(seed: String?): GameStatusReview.Status {
        return if (seed == null) GameStatusReview.Status.NEW else {
            val loadedState = gamePersistenceManager.loadState(seed)
            Timber.v("gamePersistenceManager loaded state for $seed : $loadedState")
            when (loadedState) {
                Game.State.GUESSING, Game.State.EVALUATING -> GameStatusReview.Status.ONGOING
                Game.State.WON -> GameStatusReview.Status.WON
                Game.State.LOST -> GameStatusReview.Status.LOST
                else -> GameStatusReview.Status.NEW
            }
        }
    }

    private fun performLaunch(type: GameSetupContract.Type, gameStatusReview: GameStatusReview) {
        // persist settings for this type
        val hardMode = gameStatusReview.setup.evaluation.enforced != ConstraintPolicy.IGNORE
        when (type) {
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
            GameSetupContract.Type.ONGOING -> {
                Timber.e("Asked to Launch a game from ONGOING setup type??")
            }
        }

        view?.finishGameSetup(gameStatusReview)
    }

    private fun createGameStatusReview(
        type: GameSetupContract.Type,
        seed: String?,
        gameSetup: GameSetup,
        status: GameStatusReview.Status?
    ): GameStatusReview {
        // determine purpose
        // consider current game status for type != ONGOING? This might interfere with
        // Seed status display.
        // E.g. could use "status in setOf(null, GameStatusReview.Status.NEW, GameStatusReview.Status.ONGOING, GameStatusReview.Status.LOADING)"
        // but this will hide current status text in GameReviewSeedViewHolder.
        val forLaunching = when (type) {
            GameSetupContract.Type.DAILY,
            GameSetupContract.Type.SEEDED,
            GameSetupContract.Type.CUSTOM -> true
            GameSetupContract.Type.ONGOING -> false // status == GameStatusReview.Status.ONGOING
        }
        val purpose = if (forLaunching) GameStatusReview.Purpose.LAUNCH else GameStatusReview.Purpose.EXAMINE

        // determine notes
        val notesVersionQualifier = if (seed == null) emptySet() else qualifiers.map { when (it) {
            GameSetupContract.Qualifier.VERSION_CHECK_PENDING,
            GameSetupContract.Qualifier.VERSION_CHECK_FAILED -> GameStatusReview.Note.SEED_ERA_UNDETERMINED
            GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE -> null
            GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED -> null
            GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED -> GameStatusReview.Note.SEED_FUTURISTIC
        } }.filterNotNull()

        val notesSeedEra = if (seed == null) emptySet() else when (gameSetupManager.getSeedEra(seed)) {
            GameSetupManager.SeedEra.LEGACY -> setOf(GameStatusReview.Note.SEED_LEGACY)
            GameSetupManager.SeedEra.RETIRED -> setOf(GameStatusReview.Note.SEED_RETIRED)
            GameSetupManager.SeedEra.CURRENT,
            GameSetupManager.SeedEra.FUTURISTIC,
            GameSetupManager.SeedEra.UNKNOWN -> emptySet()  // either no notes, or seed should not have reached this point
        }

        // combine notes. Strongly suspect either or both are empty in all cases, but keep this
        // construction to simplify future updates where this may no longer be the case.
        val notes = notesVersionQualifier.union(notesSeedEra)

        return GameStatusReview(seed, gameSetup, status ?: GameStatusReview.Status.NEW, purpose, notes)
    }

    /**
     * Determine whether launch of the provided [review] is allowed, returning the result and
     * (perhaps) a Qualifier that explains it.
     */
    private fun isLaunchAllowed(
        type: GameSetupContract.Type,
        qualifiers: Set<GameSetupContract.Qualifier>,
        review: GameStatusReview?
    ): Pair<Boolean, GameSetupContract.Qualifier?> {
        var qualifier: GameSetupContract.Qualifier? = null

        val canLaunchSeed = review?.seed == null
                || gameSetupManager.getSeedEra(review.seed) in setOf(GameSetupManager.SeedEra.CURRENT, GameSetupManager.SeedEra.LEGACY)

        val canLaunchForSetup = review?.setup != null

        val canLaunchForType = when (type) {
            GameSetupContract.Type.DAILY,
            GameSetupContract.Type.SEEDED,
            GameSetupContract.Type.CUSTOM -> review?.status in setOf(GameStatusReview.Status.NEW, GameStatusReview.Status.ONGOING)
            GameSetupContract.Type.ONGOING -> review?.status == GameStatusReview.Status.ONGOING
        }

        val canLaunchForQualifiers = when (type) {
            GameSetupContract.Type.DAILY -> {
                qualifier = listOf(
                    GameSetupContract.Qualifier.VERSION_CHECK_FAILED,
                    GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED,
                    GameSetupContract.Qualifier.VERSION_CHECK_PENDING
                ).intersect(qualifiers)
                    .firstOrNull()
                qualifier == null
            }
            GameSetupContract.Type.SEEDED,
            GameSetupContract.Type.CUSTOM -> {
                qualifier = listOf(
                    GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED
                ).intersect(qualifiers)
                    .firstOrNull()
                qualifier == null
            }
            GameSetupContract.Type.ONGOING -> true
        }



        return Pair(canLaunchSeed && canLaunchForSetup && canLaunchForType && canLaunchForQualifiers, qualifier ?: qualifiers.maxByOrNull { it.level.priority })
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region View UI
    //---------------------------------------------------------------------------------------------
    override fun onTypeSelected(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>) {
        if (this.type != type || this.qualifiers != qualifiers) {
            viewScope.launch {
                updateTypeAndSetup(type, qualifiers)
                updateGameSetupAndView(seed, gameSetup)
            }
        }
    }

    override fun onLaunchButtonClicked() {
        val type = this.type
        val qualifiers = this.qualifiers
        val seed = this.seed
        val gameSetup = this.gameSetup

        val launchGame: (GameStatusReview.Status?) -> Unit = {
            val review = createGameStatusReview(type, seed, gameSetup, it)
            val canLaunch = isLaunchAllowed(type, qualifiers, review)
            if (canLaunch.first) performLaunch(type, review) else {
                view?.showError(GameSetupContract.Feature.LAUNCH, GameSetupContract.Error.FEATURE_NOT_ALLOWED, canLaunch.second)
            }
        }

        viewScope.launch {
            val progress = try {
                getSessionProgress(seed)
            } catch (error: Exception) {
                // TODO use specific type of Exception
                Timber.e(error, "An error occurred checking game progress")
                null
            }
            launchGame(progress)
        }
    }

    override fun onCancelButtonClicked() {
        view?.cancelGameSetup()
    }

    override fun onSeedEntered(seed: String): Boolean {
        if (type == GameSetupContract.Type.SEEDED) {
            try {
                val modifiedSetup = gameSetupManager.getSetup(seed, gameSetupManager.isHard(gameSetup))

                // consider whether the modification is acceptable
                return when {
                    modifiedSetup.daily -> {
                        Timber.w("Daily seed $seed entered")
                        view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
                        false
                    }
                    // TODO: other seed rejection cases -- where seed is parseable but cannot be used
                    else -> {
                        // accept seed
                        updateGameSetupAndView(seed, modifiedSetup)
                        Timber.d("onSeedEntered: new setup is $gameSetup")
                        true
                    }
                }
            } catch (err: Exception) {
                Timber.w(err, "Bad seed $seed entered")
                view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_VALUE_INVALID, null)
            }
        } else {
            Timber.w("Seed entered for non-seed type $type")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // change back!
        return false
    }

    override fun onSeedRandomized(): Boolean {
        if (type == GameSetupContract.Type.SEEDED) {
            try {
                // changing the seed might change versioned features, so reconfigure them.
                updateGameSetupAndView(gameSetupManager.modifyGameSetup(gameSetup, randomized = true))
                Timber.d("seed randomized to gameSetup $gameSetup")
                return true
            } catch (err: Exception) {
                Timber.w(err, "Can't randomize seed")
                view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
            }
        } else {
            Timber.w("Seed randomized for non-seed type $type")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // refuse!
        return false
    }

    override fun onRolesEntered(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): Boolean {
        if (type == GameSetupContract.Type.CUSTOM) {
            // player role can significantly change the available game features
            updateGameSetupAndView(gameSetupManager.modifyGameSetup(gameSetup, solver = solver, evaluator = evaluator))
            Timber.d("roles entered; gameSetup to $gameSetup")
            return true
        } else {
            Timber.w("Player roles entered for non-seed type $type")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // change back!
        return false
    }

    override fun onLanguageEntered(language: CodeLanguage): Boolean {
        // accept current
        if (language == gameSetup.vocabulary.language) return true

        if (type !in setOf(GameSetupContract.Type.DAILY, GameSetupContract.Type.ONGOING)) {
            if (language != gameSetup.vocabulary.language) {
                // update the vocabulary using language defaults
                val modifiedSetup = gameSetupManager.modifyGameSetup(gameSetup, language = language)
                val roundsRecommendation = gameSetupManager.getRecommendedRounds(
                    modifiedSetup.vocabulary,
                    modifiedSetup.evaluation
                )

                updateGameSetupAndView(gameSetupManager.modifyGameSetup(
                    gameSetup,
                    language = language,
                    board = GameSetup.Board(roundsRecommendation.first)
                ))
                return true
            }
        } else {
            Timber.w("Language entered for type $type")
            view?.showError(GameSetupContract.Feature.CODE_LANGUAGE, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        return false
    }

    override fun onConstraintPolicyEntered(policy: ConstraintPolicy): Boolean {
        // accept current
        if (policy == gameSetup.evaluation.type) return true

        if (type !in setOf(GameSetupContract.Type.DAILY, GameSetupContract.Type.ONGOING)) {
            val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)

            if (policy !in languageDetails.evaluationsSupported) {
                Timber.w("Policy $policy entered, but not supported for language ${gameSetup.vocabulary.language}")
                view?.showError(GameSetupContract.Feature.CODE_EVALUATION_POLICY, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
            } else if (policy != gameSetup.evaluation.type) {
                // update the policy using language defaults
                val hardMode = gameSetup.evaluation.enforced == languageDetails.hardModeConstraint[gameSetup.evaluation.type]
                val enforced = if (hardMode) languageDetails.hardModeConstraint[policy] ?: ConstraintPolicy.IGNORE else ConstraintPolicy.IGNORE
                val evaluation = GameSetup.Evaluation(policy, enforced)

                val modifiedSetup = gameSetupManager.modifyGameSetup(gameSetup, evaluation = evaluation)

                val roundsRecommendation = gameSetupManager.getRecommendedRounds(
                    modifiedSetup.vocabulary,
                    modifiedSetup.evaluation
                )

                updateGameSetupAndView(gameSetupManager.modifyGameSetup(
                    gameSetup,
                    evaluation = evaluation,
                    board = GameSetup.Board(roundsRecommendation.first)
                ))
                return true
            } else {
                Timber.w("Policy $policy re-entered for ConstraintPolicy")
            }
        } else {
            Timber.w("ConstraintPolicy entered for type $type")
            view?.showError(GameSetupContract.Feature.CODE_EVALUATION_POLICY, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        return false
    }

    override fun onFeatureEntered(feature: GameSetupContract.Feature, active: Boolean): Boolean {
        // Boolean features: CHARACTER_REPETITIONS, EVALUATOR_HONEST, HARD_MODE
        when (feature) {
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION -> {
                // accept current
                if ((gameSetup.vocabulary.characterOccurrences > 1) == active) return true
                // allowed only as false when length <= characters
                if ((gameSetup.vocabulary.length <= gameSetup.vocabulary.characters) || active) {
                    val vocabulary: GameSetup.Vocabulary = GameSetup.Vocabulary(
                        language = gameSetup.vocabulary.language,
                        type = gameSetup.vocabulary.type,
                        length = gameSetup.vocabulary.length,
                        characters = gameSetup.vocabulary.characters,
                        characterOccurrences = if (active) gameSetup.vocabulary.length else 1
                    )
                    updateGameSetupAndView(gameSetupManager.modifyGameSetup(gameSetup, vocabulary = vocabulary))
                    Timber.d("feature $feature entered: $gameSetup")
                    return true
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
                }
            }
            GameSetupContract.Feature.HARD_MODE -> {
                // accept current
                if (gameSetupManager.isHard(gameSetup) == active) return true
                // allowed in all contexts
                updateGameSetupAndView(gameSetupManager.modifyGameSetup(gameSetup, hard = active))
                Timber.d("feature $feature entered: $gameSetup")
                return true
            }
            GameSetupContract.Feature.EVALUATOR_HONEST -> {
                // accept current
                if ((gameSetup.evaluator == GameSetup.Evaluator.HONEST) == active) return true
                // allowed only when CUSTOM and non-human evaluator
                if (type == GameSetupContract.Type.CUSTOM && gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
                    // player roles can substantially alter available features
                    updateGameSetupAndView(gameSetupManager.modifyGameSetup(
                        gameSetup,
                        evaluator = if (active) GameSetup.Evaluator.HONEST else GameSetup.Evaluator.CHEATER
                    ))
                    Timber.d("feature $feature entered: $gameSetup")
                    return true
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
                }
            }
            else -> view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_INVALID, null)
        }

        return false
    }

    override fun onFeatureEntered(feature: GameSetupContract.Feature, value: Int): Boolean {
        // Int features: CODE_LENGTH, CODE_CHARACTERS, ROUNDS
        val languageDetails = gameSetupManager.getCodeLanguageDetails(gameSetup.vocabulary.language)
        val modifiedSetup: GameSetup? = when (feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                // accept current
                if (gameSetup.vocabulary.length == value) return true
                if (value in languageDetails.codeLengthsSupported) {
                    // update vocabulary and, possibly, rounds (only to stay in bounds)
                    val noRepetitionSupported = value <= gameSetup.vocabulary.characters
                    val vocabulary: GameSetup.Vocabulary = GameSetup.Vocabulary(
                        language = gameSetup.vocabulary.language,
                        type = gameSetup.vocabulary.type,
                        length = value,
                        characters = gameSetup.vocabulary.characters,
                        characterOccurrences = if (gameSetup.vocabulary.characterOccurrences == 1 && noRepetitionSupported) 1 else value
                    )

                    // possibly adjust rounds based on maximum
                    val roundsRecommendation = gameSetupManager.getRecommendedRounds(vocabulary, gameSetup.evaluation)
                    if (gameSetup.board.rounds <= roundsRecommendation.second) {
                        gameSetupManager.modifyGameSetup(gameSetup, vocabulary = vocabulary)
                    } else {
                        gameSetupManager.modifyGameSetup(
                            gameSetup,
                            vocabulary = vocabulary,
                            board = GameSetup.Board(roundsRecommendation.first)
                        )
                    }
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
                    null
                }
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                // accept current
                if (gameSetup.vocabulary.characters == value) return true
                if (value in languageDetails.codeCharactersSupported) {
                    val noRepetitionSupported = gameSetup.vocabulary.length <= value
                    val vocabulary = GameSetup.Vocabulary(
                        language = gameSetup.vocabulary.language,
                        type = gameSetup.vocabulary.type,
                        length = gameSetup.vocabulary.length,
                        characters = value,
                        characterOccurrences = if (gameSetup.vocabulary.characterOccurrences == 1 && noRepetitionSupported) 1 else gameSetup.vocabulary.length
                    )

                    // possibly adjust rounds based on maximum
                    val roundsRecommendation = gameSetupManager.getRecommendedRounds(vocabulary, gameSetup.evaluation)
                    if (gameSetup.board.rounds <= roundsRecommendation.second) {
                        gameSetupManager.modifyGameSetup(gameSetup, vocabulary = vocabulary)
                    } else {
                        gameSetupManager.modifyGameSetup(
                            gameSetup,
                            vocabulary = vocabulary,
                            board = GameSetup.Board(roundsRecommendation.first)
                        )
                    }
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
                    null
                }
            }
            GameSetupContract.Feature.ROUNDS -> {
                // accept current
                if (gameSetup.board.rounds == value) return true
                val roundsRecommendation = gameSetupManager.getRecommendedRounds(gameSetup.vocabulary, gameSetup.evaluation)
                val rounds = (0..(roundsRecommendation.second))
                if (value in rounds) {
                    gameSetupManager.modifyGameSetup(
                        gameSetup,
                        board = GameSetup.Board(value)
                    )
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
                    null
                }
            }
            else -> {
                view?.showError(feature, GameSetupContract.Error.FEATURE_VALUE_INVALID, null)
                null
            }
        }

        if (modifiedSetup != null) {
            updateGameSetupAndView(modifiedSetup)
            Timber.d("setup revised: $gameSetup")
            return true
        }

        return false
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}