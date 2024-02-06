package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.defaults.GameDefaultsManager
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.game.setup.GameSetupManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale
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

    // Configuration Helper
    private var gameSetupHelper: GameSetupHelper = GameSetupHelper()

    // Caching View Updater
    private var viewUpdateHelper = ViewUpdateHelper()

    // Persisted View state
    private var savedViewState: SavedViewState? = null

    override fun onAttached() {
        super.onAttached()

        // reset the updater's cache
        viewUpdateHelper.reset()

        // create the game setup helper; this pushes an asynchronous update
        if (savedViewState?.matchesState(view!!) != true) {
            // gameSetupHelper is not configured appropriately; create a new one
            Timber.v("view state does not match for ${view!!.getType()}; initializing gameSetupHelper")
            gameSetupHelper = initializeGameSetupHelper(
                view!!.getType(),
                view!!.getQualifiers(),
                view!!.getOngoingGameSetup(),
                gameSetupHelper
            )
        }

        // force a view update
        gameSetupHelper.updateView(true)

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

    private fun initializeGameSetupHelper(
        type: GameSetupContract.Type,
        qualifiers: Set<GameSetupContract.Qualifier>,
        seedAndSetup: Pair<String?, GameSetup>?,
        gameSetupHelper: GameSetupHelper? = null
    ): GameSetupHelper {
        val setup = try {
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

        val seed = gameSetupManager.getSeed(setup)

        return if (gameSetupHelper == null) GameSetupHelper(type, qualifiers, seed, setup) else {
            gameSetupHelper.reset(type, qualifiers, seed, setup)
            gameSetupHelper
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Type, Feature, Progress Support
    //---------------------------------------------------------------------------------------------

    private interface GameSetupConfiguration {
        val type: GameSetupContract.Type
        val qualifiers: Set<GameSetupContract.Qualifier>
        val seed: String?
        val setup: GameSetup
        val review: GameStatusReview
        val saveData: GameSaveData?
    }

    private data class ConfigurationState(
        override val type: GameSetupContract.Type,
        override val qualifiers: Set<GameSetupContract.Qualifier>,
        override val seed: String?,
        override val setup: GameSetup,
        override val review: GameStatusReview,
        override val saveData: GameSaveData?
    ): GameSetupConfiguration {
        constructor(
            configuration: GameSetupConfiguration,
            review: GameStatusReview? = null
        ): this(
            type = configuration.type,
            qualifiers = configuration.qualifiers,
            seed = configuration.seed,
            setup = configuration.setup,
            review = review ?: configuration.review,
            saveData = configuration.saveData
        )
    }

    private fun updateView(configuration: GameSetupConfiguration) {
        updateViewCodeLanguage(configuration)
        updateViewFeatureAvailability(configuration)
        updateViewFeatureRanges(configuration)
        updateViewGameStatusReview(configuration)
    }

    private fun updateViewCodeLanguage(configuration: GameSetupConfiguration) {
        val characters = gameCreationManager.getCodeCharacters(configuration.setup)
        val locale = configuration.setup.vocabulary.language.locale
        if (locale != null) {
            viewUpdateHelper.setCodeLanguage(view, characters, locale)
        } else {
            viewUpdateHelper.setCodeComposition(view, characters)
        }
    }

    private fun updateViewFeatureAvailability(configuration: GameSetupConfiguration) {
        // some metadata
        val type = configuration.type
        val setup = configuration.setup
        val vocabulary = configuration.setup.vocabulary
        val evaluation = configuration.setup.evaluation
        val saveData = configuration.saveData

        val langDeets = gameSetupManager.getCodeLanguageDetails(vocabulary.language)
        val roundRec = gameSetupManager.getRecommendedRounds(vocabulary, evaluation)

        val availabilityMap: MutableMap<GameSetupContract.Feature, GameSetupContract.Availability> = mutableMapOf()
        val qualifierMap: MutableMap<GameSetupContract.Feature, GameSetupContract.Qualifier> = mutableMapOf()

        // only include LAUNCH if actually available; e.g. for DAILY, check if not yet complete
        val canLaunch: Pair<Boolean, GameSetupContract.Qualifier?> = isLaunchAllowed(configuration)
        val canLaunchQualifier = canLaunch.second
        availabilityMap[GameSetupContract.Feature.LAUNCH] = if (canLaunch.first) {
            GameSetupContract.Availability.AVAILABLE
        } else {
            GameSetupContract.Availability.LOCKED
        }
        if (canLaunchQualifier != null) qualifierMap[GameSetupContract.Feature.LAUNCH] = canLaunchQualifier

        // hard mode is disabled for some game types. For the rest, it should be locked if the game
        // is in-progress and not already set to hard (can be disabled, but not enabled).
        val gameExists = saveData != null
        val gameBegun = saveData != null && saveData.started
        val gameOver = saveData != null && saveData.over
        availabilityMap[GameSetupContract.Feature.HARD_MODE] = when {
            langDeets.hardModeConstraint[evaluation.type] == null -> GameSetupContract.Availability.DISABLED
            gameBegun && (gameOver || !gameSetupManager.isHard(setup)) -> GameSetupContract.Availability.LOCKED
            else -> GameSetupContract.Availability.AVAILABLE
        }

        // Number of rounds. Locked for dailies, and games with only one valid
        // rounds settings (e.g. games already in progress).
        val gameRound = saveData?.round ?: 1
        availabilityMap[GameSetupContract.Feature.ROUNDS] = if (
            gameOver
            || setup.daily
            || (gameRound..(roundRec.second)).toList().size <= 1
        ) GameSetupContract.Availability.LOCKED else GameSetupContract.Availability.AVAILABLE

        // language: locked for in-progress, disabled for dailies
        val languageAvailability = when {
            type == GameSetupContract.Type.DAILY || setup.daily -> GameSetupContract.Availability.DISABLED
            type == GameSetupContract.Type.ONGOING || gameExists -> GameSetupContract.Availability.LOCKED
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
                if (vocabulary.length <= vocabulary.characters) {
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
                if (setup.solver == GameSetup.Solver.PLAYER && setup.evaluator == GameSetup.Evaluator.HONEST) {
                    GameSetupContract.Availability.LOCKED
                } else {
                    GameSetupContract.Availability.DISABLED
                }
            }
        }

        if (gameSetupHelper.type == GameSetupContract.Type.CUSTOM) {
            // TODO when player role is implemented, allow this
            // features[GameSetupContract.Feature.PLAYER_ROLE] = GameSetupContract.Availability.AVAILABLE

            if (setup.evaluator != GameSetup.Evaluator.PLAYER) {
                availabilityMap[GameSetupContract.Feature.EVALUATOR_HONEST] = GameSetupContract.Availability.AVAILABLE
            }
        }

        // anything unspecified is DISABLED.
        viewUpdateHelper.setFeatureAvailability(view, availabilityMap, qualifierMap)
    }

    private fun updateViewFeatureRanges(configuration: GameSetupConfiguration) {
        val vocabulary = configuration.setup.vocabulary
        val evaluation = configuration.setup.evaluation
        val gameRound = configuration.saveData?.round ?: 1

        val languageDetails = gameSetupManager.getCodeLanguageDetails(vocabulary.language)

        viewUpdateHelper.setLanguagesAllowed(view, listOf(CodeLanguage.ENGLISH, CodeLanguage.CODE))
        viewUpdateHelper.setFeatureValuesAllowed(view, GameSetupContract.Feature.CODE_LENGTH, languageDetails.codeLengthsSupported)
        viewUpdateHelper.setFeatureValuesAllowed(view, GameSetupContract.Feature.CODE_CHARACTERS, languageDetails.codeCharactersSupported)
        viewUpdateHelper.setEvaluationPoliciesAllowed(view, languageDetails.evaluationsSupported)

        // TODO recommend number of rounds based on vocabulary
        val roundRec = gameSetupManager.getRecommendedRounds(vocabulary, evaluation)
        viewUpdateHelper.setFeatureValuesAllowed(
            view,
            GameSetupContract.Feature.ROUNDS,
            listOf(0) + (gameRound..(roundRec.second)).toList()
        )
    }

    private fun updateViewGameStatusReview(configuration: GameSetupConfiguration) {
        val canLaunch = isLaunchAllowed(configuration)
        viewUpdateHelper.setGameStatusReview(view, configuration.review)
        viewUpdateHelper.setFeatureAvailability(
            view,
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
        val hardMode = gameSetupManager.isHard(gameStatusReview.setup)
        when (type) {
            GameSetupContract.Type.DAILY -> {
                gameDefaultsManager.hardMode = hardMode
                gameDefaultsManager.put(DAILY_DEFAULTS_KEY, gameStatusReview.setup)
            }
            GameSetupContract.Type.SEEDED -> {
                gameDefaultsManager.put(gameStatusReview.setup)
                gameDefaultsManager.put(SEEDED_DEFAULTS_KEY, gameStatusReview.setup)
            }
            GameSetupContract.Type.CUSTOM -> {
                if (gameStatusReview.setup.solver == GameSetup.Solver.PLAYER) {
                    gameDefaultsManager.hardMode = hardMode
                    gameDefaultsManager.put(CUSTOM_DEFAULTS_KEY, gameStatusReview.setup)
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
        qualifiers: Set<GameSetupContract.Qualifier>,
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
        val notesVersionQualifier = if (seed == null) emptySet() else qualifiers.mapNotNull {
            when (it) {
                GameSetupContract.Qualifier.VERSION_CHECK_PENDING,
                GameSetupContract.Qualifier.VERSION_CHECK_FAILED -> GameStatusReview.Note.SEED_ERA_UNDETERMINED

                GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE -> null
                GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED -> null
                GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED -> GameStatusReview.Note.SEED_FUTURISTIC
            }
        }

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
        configuration: GameSetupConfiguration
    ): Pair<Boolean, GameSetupContract.Qualifier?> {
        var qualifier: GameSetupContract.Qualifier? = null

        val type = configuration.type
        val qualifiers = configuration.qualifiers
        val seed = configuration.seed
        val setup = configuration.setup
        val review = configuration.review

        val canLaunchSeed = seed == null
                || gameSetupManager.getSeedEra(seed) in setOf(
            GameSetupManager.SeedEra.CURRENT,
            GameSetupManager.SeedEra.LEGACY
        )

        val canLaunchForSetup = true // setup != null

        val canLaunchForType = when (type) {
            GameSetupContract.Type.DAILY,
            GameSetupContract.Type.SEEDED,
            GameSetupContract.Type.CUSTOM -> review.status in setOf(
                GameStatusReview.Status.NEW,
                GameStatusReview.Status.ONGOING
            )

            GameSetupContract.Type.ONGOING -> review.status == GameStatusReview.Status.ONGOING
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

        return Pair(
            canLaunchSeed && canLaunchForSetup && canLaunchForType && canLaunchForQualifiers,
            qualifier ?: qualifiers.maxByOrNull { it.level.priority })
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Configuration Helper
    //---------------------------------------------------------------------------------------------

    /**
     * A data-helper that maintains consistency between different representations of the
     * game setup, handles background loads to check game progress, and pushes view updates
     * when appropriate.
     */
    private inner class GameSetupHelper(): GameSetupConfiguration {

        private lateinit var _type: GameSetupContract.Type
        override val type: GameSetupContract.Type
            get() = _type

        private lateinit var _qualifiers: Set<GameSetupContract.Qualifier>
        override var qualifiers: Set<GameSetupContract.Qualifier>
            get() = _qualifiers
            set(value) {
                if (_qualifiers != value) {
                    _qualifiers = value
                    onQualifiersChanged(value)
                }
            }

        private var _seed: String? = null
        override var seed: String?
            get() = _seed
            set(value) {
                if (_seed != value) {
                    _seed = value
                    onSeedChanged(value)
                }
            }

        private lateinit var _setup: GameSetup
        override var setup: GameSetup
            get() = _setup
            set(value) {
                if (_setup != value) {
                    _setup = value
                    onSetupChanged(value)
                }
            }

        private var _review: GameStatusReview? = null
        override val review: GameStatusReview
            get() = _review ?: createGameStatusReview(type, qualifiers, seed, setup, GameStatusReview.Status.LOADING)

        private var _saveData: GameSaveData? = null
        override val saveData: GameSaveData?
            get() = _saveData

        private var updateViewJob: Job? = null
        private var loadJob: Job? = null

        constructor(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>, seed: String?, setup: GameSetup): this() {
            reset(type, qualifiers, seed, setup)
        }

        fun reset(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>, seed: String?, setup: GameSetup) {
            _type = type
            _qualifiers = qualifiers
            _seed = seed
            _setup = setup
            _review = null
            _saveData = null

            updateReview()
        }

        private fun onQualifiersChanged(qualifiers: Set<GameSetupContract.Qualifier>) {
            // qualifiers affect GameStatusReview
            updateReview()

            // update view
            updateView()
        }

        private fun onSeedChanged(seed: String?) {
            // update setup synchronously
            val oldSetup = this.setup
            val hardMode = gameSetupManager.isHard(oldSetup)
            if (seed != null) setup = gameSetupManager.getSetup(seed, hardMode)
            // updating the setup always updates review and view; nothing else to do
        }
        private fun onSetupChanged(setup: GameSetup) {
            // update seed and game review synchronously
            seed = gameSetupManager.getSeed(setup)

            // update review
            updateReview()

            // update view
            updateView()
        }

        private fun updateReview() {
            val oldReview = _review
            val status = when (seed) {
                null -> GameStatusReview.Status.NEW
                oldReview?.seed -> oldReview?.status ?: GameStatusReview.Status.LOADING
                else -> GameStatusReview.Status.LOADING
            }
            val newReview = createGameStatusReview(type, qualifiers, seed, setup, status)

            if (oldReview == newReview) return

            // update Review, but only cancel pending load if seed changed
            _review = newReview
            if (oldReview?.seed == newReview.seed) return

            loadJob?.cancel("Seed changed")

            // only load if status is LOADING
            if (status != GameStatusReview.Status.LOADING) return

            // the loadJob will be canceled if any inputs change
            loadJob = viewScope.launch {
                ensureActive()
                val saveData = if (review.seed == null) null else gamePersistenceManager.load(review.seed)
                ensureActive()
                // sanity check; should always be true if the job wasn't canceled, but just to be safe
                if (newReview.seed == seed) {
                    _review = createGameStatusReview(type, qualifiers, seed, setup, when(saveData?.state) {
                        Game.State.GUESSING, Game.State.EVALUATING -> GameStatusReview.Status.ONGOING
                        Game.State.WON -> GameStatusReview.Status.WON
                        Game.State.LOST -> GameStatusReview.Status.LOST
                        else -> GameStatusReview.Status.NEW
                    })
                    _saveData = saveData

                    updateView()
                }
            }
        }

        fun updateView(force: Boolean = false) {
            updateViewJob?.cancel("New view update available")
            val configuration = ConfigurationState(this)

            if (force) updateView(configuration) else updateViewJob = viewScope.launch { updateView(configuration) }
        }
        
        fun getConfiguration(): GameSetupConfiguration = ConfigurationState(this)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region View Update Helper
    //---------------------------------------------------------------------------------------------

    /**
     * A class with no access to GameSetupPresenter's field. Used to push updates for Feature
     * support up to the View in a way that minimizes redundant updates. Caches updates pushed
     * through so subsequent updates can only be forwarded if they change the view's state from
     * the previous update.
     *
     * This class is used to simplify the update pipeline when feature availability is determined
     * bit-by-bit as asynchronous operations are completed.
     *
     * Does not keep a reference to the View (to avoid leaks). Ensure that the same View instance
     * is passed with each function call, or caching will be inaccurate.
     *
     * If the View changes, use [reset] to clear the cache, or simply change to a new instance.
     */
    private class ViewUpdateHelper {

        var gameStatusReview: GameStatusReview? = null
        var characters: List<Char>? = null
        var locale: Locale? = null
        var featureAvailability: MutableMap<GameSetupContract.Feature, GameSetupContract.Availability> = mutableMapOf()
        var featureQualifiers: MutableMap<GameSetupContract.Feature, GameSetupContract.Qualifier> = mutableMapOf()
        var featureValuesAllowed: MutableMap<GameSetupContract.Feature, List<Int>> = mutableMapOf()
        var languagesAllowed: List<CodeLanguage>? = null
        var evaluationPoliciesAllowed: List<ConstraintPolicy>? = null

        fun reset() {
            gameStatusReview = null
            characters = null
            locale = null
            featureAvailability.clear()
            featureQualifiers.clear()
            featureValuesAllowed.clear()
            languagesAllowed = null
            evaluationPoliciesAllowed = null
        }

        fun setGameStatusReview(view: GameSetupContract.View?, gameStatusReview: GameStatusReview) {
            if (view == null) return
            if (this.gameStatusReview != gameStatusReview) {
                this.gameStatusReview = gameStatusReview
                view.setGameStatusReview(gameStatusReview)
            }
        }
        fun setCodeLanguage(view: GameSetupContract.View?, characters: Iterable<Char>, locale: Locale) {
            if (view == null) return

            val asList = characters.toList()
            if (this.characters != asList || this.locale != locale) {
                this.characters = asList
                this.locale = locale
                view.setCodeLanguage(asList, locale)
            }
        }

        fun setCodeComposition(view: GameSetupContract.View?, characters: Iterable<Char>) {
            if (view == null) return

            val asList = characters.toList()
            if (this.characters != asList || this.locale != null) {
                this.characters = asList
                this.locale = null
                view.setCodeComposition(asList)
            }
        }

        fun setFeatureAvailability(
            view: GameSetupContract.View?,
            availabilities: Map<GameSetupContract.Feature, GameSetupContract.Availability>,
            qualifiers: Map<GameSetupContract.Feature, GameSetupContract.Qualifier>,
            defaultAvailability: GameSetupContract.Availability? = GameSetupContract.Availability.DISABLED
        ) {
            if (view == null) return

            val updatedFeatures = GameSetupContract.Feature.entries.filter {
                val availability = availabilities[it] ?: defaultAvailability
                if (availability == null) false else {
                    updateFeatureAvailability(view, it, availability, qualifiers[it], false)
                }
            }

            view.setFeatureAvailability(
                this.featureAvailability.filter { it.key in updatedFeatures },
                this.featureQualifiers.filter { it.key in updatedFeatures },
                null
            )
        }

        fun setFeatureAvailability(
            view: GameSetupContract.View?,
            feature: GameSetupContract.Feature,
            availability: GameSetupContract.Availability,
            qualifier: GameSetupContract.Qualifier?
        ) {
            if (view == null) return
            updateFeatureAvailability(view, feature, availability, qualifier, true)
        }

        private fun updateFeatureAvailability(
            view: GameSetupContract.View,
            feature: GameSetupContract.Feature,
            availability: GameSetupContract.Availability,
            qualifier: GameSetupContract.Qualifier?,
            push: Boolean
        ): Boolean {
            if (featureAvailability[feature] == availability && featureQualifiers[feature] == qualifier) {
                return false
            }

            featureAvailability[feature] = availability
            if (qualifier != null) featureQualifiers[feature] =
                qualifier else featureQualifiers.remove(feature)

            if (push) view.setFeatureAvailability(feature, availability, qualifier)
            return true
        }

        fun setFeatureValuesAllowed(view: GameSetupContract.View?, feature: GameSetupContract.Feature, values: List<Int>) {
            if (view == null) return

            if (values != this.featureValuesAllowed[feature]) {
                this.featureValuesAllowed[feature] = values
                view.setFeatureValuesAllowed(feature, values)
            }
        }

        fun setLanguagesAllowed(view: GameSetupContract.View?, languages: List<CodeLanguage>) {
            if (view == null) return

            if (languages != languagesAllowed) {
                languagesAllowed = languages
                view.setLanguagesAllowed(languages)
            }
        }

        fun setEvaluationPoliciesAllowed(view: GameSetupContract.View?, policies: List<ConstraintPolicy>) {
            if (view == null) return

            if (evaluationPoliciesAllowed != policies) {
                evaluationPoliciesAllowed = policies
                view.setEvaluationPoliciesAllowed(policies)
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region View UI
    //---------------------------------------------------------------------------------------------
    override fun onTypeSelected(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>) {
        if (gameSetupHelper.type != type || gameSetupHelper.qualifiers != qualifiers) {
            val ongoingSetup = view?.getOngoingGameSetup()
            viewScope.launch {
                gameSetupHelper = initializeGameSetupHelper(type, qualifiers, ongoingSetup, gameSetupHelper)
                gameSetupHelper.updateView()
            }
        }
    }

    override fun onLaunchButtonClicked() {
        var configuration = gameSetupHelper.getConfiguration()
        
        viewScope.launch { 
            // if loading, independently verify status
            if (configuration.review.status == GameStatusReview.Status.LOADING) {
                val status = getSessionProgress(configuration.seed)
                configuration = ConfigurationState(
                    configuration,
                    review = createGameStatusReview(
                        configuration.type, 
                        configuration.qualifiers, 
                        configuration.seed, 
                        configuration.setup, 
                        status
                    )
                )
            }
            
            val canLaunch = isLaunchAllowed(configuration)
            if (canLaunch.first) performLaunch(configuration.type, configuration.review) else {
                view?.showError(GameSetupContract.Feature.LAUNCH, GameSetupContract.Error.FEATURE_NOT_ALLOWED, canLaunch.second)
            }
        }
    }

    override fun onCancelButtonClicked() {
        view?.cancelGameSetup()
    }

    override fun onSeedEntered(seed: String): Boolean {
        if (gameSetupHelper.type == GameSetupContract.Type.SEEDED) {
            try {
                val modifiedSetup = gameSetupManager.getSetup(seed, gameSetupManager.isHard(gameSetupHelper.setup))

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
                        gameSetupHelper.setup = modifiedSetup
                        Timber.d("onSeedEntered: new setup is $modifiedSetup")
                        true
                    }
                }
            } catch (err: Exception) {
                Timber.w(err, "Bad seed $seed entered")
                view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_VALUE_INVALID, null)
            }
        } else {
            Timber.w("Seed entered for non-seed type ${gameSetupHelper.type}")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // change back!
        return false
    }

    override fun onSeedRandomized(): Boolean {
        if (gameSetupHelper.type == GameSetupContract.Type.SEEDED) {
            try {
                gameSetupHelper.setup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, randomized = true)
                Timber.d("seed randomized to gameSetup $${gameSetupHelper.setup}")
                return true
            } catch (err: Exception) {
                Timber.w(err, "Can't randomize seed")
                view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
            }
        } else {
            Timber.w("Seed randomized for non-seed type ${gameSetupHelper.type}")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // refuse!
        return false
    }

    override fun onRolesEntered(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): Boolean {
        if (gameSetupHelper.type == GameSetupContract.Type.CUSTOM) {
            // player role can significantly change the available game features
            gameSetupHelper.setup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, solver = solver, evaluator = evaluator)
            Timber.d("roles entered; gameSetup to ${gameSetupHelper.setup}")
            return true
        } else {
            Timber.w("Player roles entered for non-seed type ${gameSetupHelper.type}")
            view?.showError(GameSetupContract.Feature.SEED, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        // change back!
        return false
    }

    override fun onLanguageEntered(language: CodeLanguage): Boolean {
        val setup = gameSetupHelper.setup
        // accept current
        if (language == setup.vocabulary.language) return true

        if (gameSetupHelper.type !in setOf(GameSetupContract.Type.DAILY, GameSetupContract.Type.ONGOING)) {
            if (language != setup.vocabulary.language) {
                // update the vocabulary using language defaults
                val modifiedSetup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, language = language)
                val roundsRecommendation = gameSetupManager.getRecommendedRounds(
                    modifiedSetup.vocabulary,
                    modifiedSetup.evaluation
                )

                gameSetupHelper.setup = gameSetupManager.modifyGameSetup(
                    setup,
                    language = language,
                    board = GameSetup.Board(roundsRecommendation.first)
                )
                return true
            }
        } else {
            Timber.w("Language entered for type ${gameSetupHelper.type}")
            view?.showError(GameSetupContract.Feature.CODE_LANGUAGE, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        return false
    }

    override fun onConstraintPolicyEntered(policy: ConstraintPolicy): Boolean {
        val setup = gameSetupHelper.setup
        // accept current
        if (policy == setup.evaluation.type) return true

        if (gameSetupHelper.type !in setOf(GameSetupContract.Type.DAILY, GameSetupContract.Type.ONGOING)) {
            val languageDetails = gameSetupManager.getCodeLanguageDetails(setup.vocabulary.language)

            if (policy !in languageDetails.evaluationsSupported) {
                Timber.w("Policy $policy entered, but not supported for language ${setup.vocabulary.language}")
                view?.showError(GameSetupContract.Feature.CODE_EVALUATION_POLICY, GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED, null)
            } else if (policy != setup.evaluation.type) {
                // update the policy using language defaults
                val hardMode = setup.evaluation.enforced == languageDetails.hardModeConstraint[setup.evaluation.type]
                val enforced = if (hardMode) languageDetails.hardModeConstraint[policy] ?: ConstraintPolicy.IGNORE else ConstraintPolicy.IGNORE
                val evaluation = GameSetup.Evaluation(policy, enforced)

                val modifiedSetup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, evaluation = evaluation)

                val roundsRecommendation = gameSetupManager.getRecommendedRounds(
                    modifiedSetup.vocabulary,
                    modifiedSetup.evaluation
                )

                gameSetupHelper.setup = gameSetupManager.modifyGameSetup(
                    setup,
                    evaluation = evaluation,
                    board = GameSetup.Board(roundsRecommendation.first)
                )
                return true
            } else {
                Timber.w("Policy $policy re-entered for ConstraintPolicy")
            }
        } else {
            Timber.w("ConstraintPolicy entered for type ${gameSetupHelper.type}")
            view?.showError(GameSetupContract.Feature.CODE_EVALUATION_POLICY, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
        }

        return false
    }

    override fun onFeatureEntered(feature: GameSetupContract.Feature, active: Boolean): Boolean {
        // Boolean features: CHARACTER_REPETITIONS, EVALUATOR_HONEST, HARD_MODE
        val setup = gameSetupHelper.setup
        when (feature) {
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION -> {
                // accept current
                if ((setup.vocabulary.characterOccurrences > 1) == active) return true
                // allowed only as false when length <= characters
                if ((setup.vocabulary.length <= setup.vocabulary.characters) || active) {
                    val vocabulary: GameSetup.Vocabulary = GameSetup.Vocabulary(
                        language = setup.vocabulary.language,
                        type = setup.vocabulary.type,
                        length = setup.vocabulary.length,
                        characters = setup.vocabulary.characters,
                        characterOccurrences = if (active) setup.vocabulary.length else 1
                    )
                    gameSetupHelper.setup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, vocabulary = vocabulary)
                    Timber.d("feature $feature entered: ${gameSetupHelper.setup}")
                    return true
                } else {
                    view?.showError(feature, GameSetupContract.Error.FEATURE_NOT_ALLOWED, null)
                }
            }
            GameSetupContract.Feature.HARD_MODE -> {
                // accept current
                if (gameSetupManager.isHard(setup) == active) return true
                // allowed in all contexts
                gameSetupHelper.setup = gameSetupManager.modifyGameSetup(gameSetupHelper.setup, hard = active)
                Timber.d("feature $feature entered: ${gameSetupHelper.setup}")
                return true
            }
            GameSetupContract.Feature.EVALUATOR_HONEST -> {
                // accept current
                if ((setup.evaluator == GameSetup.Evaluator.HONEST) == active) return true
                // allowed only when CUSTOM and non-human evaluator
                if (gameSetupHelper.type == GameSetupContract.Type.CUSTOM && setup.evaluator != GameSetup.Evaluator.PLAYER) {
                    // player roles can substantially alter available features
                    gameSetupHelper.setup = gameSetupManager.modifyGameSetup(
                        setup,
                        evaluator = if (active) GameSetup.Evaluator.HONEST else GameSetup.Evaluator.CHEATER
                    )
                    Timber.d("feature $feature entered: ${gameSetupHelper.setup}")
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
        val setup = gameSetupHelper.setup
        val languageDetails = gameSetupManager.getCodeLanguageDetails(setup.vocabulary.language)
        val modifiedSetup: GameSetup? = when (feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                // accept current
                if (setup.vocabulary.length == value) return true
                if (value in languageDetails.codeLengthsSupported) {
                    // update vocabulary and, possibly, rounds (only to stay in bounds)
                    val noRepetitionSupported = value <= setup.vocabulary.characters
                    val vocabulary: GameSetup.Vocabulary = GameSetup.Vocabulary(
                        language = setup.vocabulary.language,
                        type = setup.vocabulary.type,
                        length = value,
                        characters = setup.vocabulary.characters,
                        characterOccurrences = if (setup.vocabulary.characterOccurrences == 1 && noRepetitionSupported) 1 else value
                    )

                    // possibly adjust rounds based on maximum
                    val roundsRecommendation = gameSetupManager.getRecommendedRounds(vocabulary, setup.evaluation)
                    if (setup.board.rounds <= roundsRecommendation.second) {
                        gameSetupManager.modifyGameSetup(gameSetupHelper.setup, vocabulary = vocabulary)
                    } else {
                        gameSetupManager.modifyGameSetup(
                            setup,
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
                if (setup.vocabulary.characters == value) return true
                if (value in languageDetails.codeCharactersSupported) {
                    val noRepetitionSupported = setup.vocabulary.length <= value
                    val vocabulary = GameSetup.Vocabulary(
                        language = setup.vocabulary.language,
                        type = setup.vocabulary.type,
                        length = setup.vocabulary.length,
                        characters = value,
                        characterOccurrences = if (setup.vocabulary.characterOccurrences == 1 && noRepetitionSupported) 1 else setup.vocabulary.length
                    )

                    // possibly adjust rounds based on maximum
                    val roundsRecommendation = gameSetupManager.getRecommendedRounds(vocabulary, setup.evaluation)
                    if (setup.board.rounds <= roundsRecommendation.second) {
                        gameSetupManager.modifyGameSetup(gameSetupHelper.setup, vocabulary = vocabulary)
                    } else {
                        gameSetupManager.modifyGameSetup(
                            setup,
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
                if (setup.board.rounds == value) return true
                val roundsRecommendation = gameSetupManager.getRecommendedRounds(setup.vocabulary, setup.evaluation)
                val rounds = (0..(roundsRecommendation.second))
                if (value in rounds) {
                    gameSetupManager.modifyGameSetup(
                        setup,
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
            gameSetupHelper.setup = modifiedSetup
            Timber.d("setup revised: ${gameSetupHelper.setup}")
            return true
        }

        return false
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}