package com.peaceray.codeword.domain.manager.game.impl.session

import android.content.Context
import android.content.res.AssetManager
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameSessionManager
import com.peaceray.codeword.domain.manager.game.impl.session.legacy.V01GameSaveData
import com.peaceray.codeword.domain.manager.game.impl.session.legacy.V01GameSetup
import com.peaceray.codeword.domain.manager.settings.BotSettingsManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.*
import com.peaceray.codeword.game.bot.modules.generation.*
import com.peaceray.codeword.game.bot.modules.generation.enumeration.*
import com.peaceray.codeword.game.bot.modules.generation.vocabulary.*
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumInvertedScorer
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.bot.modules.selection.StochasticThresholdScoreSelector
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.data.Settings
import com.peaceray.codeword.game.validators.Validator
import com.peaceray.codeword.game.validators.Validators
import com.peaceray.codeword.glue.ForApplication
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSessionManagerImpl @Inject constructor(
    @ForApplication val context: Context,
    @ForApplication val assets: AssetManager,
    val botSettingsManager: BotSettingsManager
): GameSessionManager {

    //region Game Setup
    //---------------------------------------------------------------------------------------------
    override fun getGame(seed: String?, setup: GameSetup, create: Boolean): Game {
        if (!create) {
            return loadGame(seed, setup)?.second ?: getGame(seed, setup, create = true)
        }

        return Game(getSettings(setup), getValidator(setup))
    }

    override fun getGame(save: GameSaveData) = Game.atMove(
        save.settings,
        getValidator(save.setup),
        save.uuid,
        save.constraints,
        save.currentGuess
    )

    override fun getSettings(setup: GameSetup) = Settings(
        letters = setup.vocabulary.length,
        rounds = if (setup.board.rounds > 0) setup.board.rounds else 100000,
        constraintPolicy = setup.evaluation.enforced
    )

    override fun getSolver(setup: GameSetup): Solver {
        // TODO caching

        return when(setup.solver) {
            GameSetup.Solver.BOT -> {
                var weight: (String) -> Double = { 1.0 }
                if (setup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
                    val commonWords = getWordList(setup.vocabulary.length, WordListType.SECRETS, portion = 0.9f).toHashSet()
                    val notRareWords = getWordList(setup.vocabulary.length,
                        WordListType.SECRETS, portion = 0.99f).toHashSet()
                    weight = { if (it in commonWords) 100.0 else if (it in notRareWords) 10.0 else 1.0 }
                }

                ModularSolver(
                    getGenerator(setup),
                    InformationGainScorer(setup.evaluation.type, weight),
                    StochasticThresholdScoreSelector(
                        threshold = botSettingsManager.solverStrength.toDouble(),
                        solutionBias = 1 - botSettingsManager.solverStrength.toDouble(),
                        seed = getRandomSeed(setup))   // TODO difficulty
                )
            }
            else -> throw IllegalArgumentException("Can't create a solver of type ${setup.solver}")
        }
    }

    override fun getEvaluator(setup: GameSetup): Evaluator {
        // TODO caching

        val randomSeed = getRandomSeed(setup)

        // Explicit Secret? This is a Genie feature
        if (setup.vocabulary.secret != null) {
            return ModularHonestEvaluator(
                OneCodeGenerator(setup.vocabulary.secret),
                UnitScorer(),
                RandomSelector(solutions = true, seed = randomSeed)
            )
        }

        return when(setup.evaluator) {
            GameSetup.Evaluator.HONEST -> ModularHonestEvaluator(
                getGenerator(setup, evaluator = true),
                UnitScorer(),
                RandomSelector(solutions = true, seed = randomSeed)
            )
            GameSetup.Evaluator.CHEATER -> {
                val threshold = botSettingsManager.cheaterStrength.toDouble()
                ModularFlexibleEvaluator(
                    getGenerator(setup, evaluator = true),
                    KnuthMinimumInvertedScorer(setup.evaluation.type),
                    StochasticThresholdScoreSelector(
                        threshold = threshold,
                        solutions = true,
                        invert = true
                    )
                )
            }
            else -> throw IllegalArgumentException("Can't create an evaluator of type ${setup.evaluator}")
        }
    }

    override fun getCodeCharacters(setup: GameSetup): Iterable<Char> {
        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> getCharRange(26)
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> getCharRange(setup.vocabulary.characters)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Intermediate Object Creation
    //---------------------------------------------------------------------------------------------
    private fun getValidator(setup: GameSetup, vocabulary: Boolean = true, occurrences: Boolean = true): Validator {
        val validators = mutableListOf<Validator>()

        if (vocabulary) {
            validators.add(when (setup.vocabulary.type) {
                GameSetup.Vocabulary.VocabularyType.LIST -> {
                    val wordList = getWordList(setup.vocabulary.length, WordListType.VALID)
                    Validators.words(wordList)
                }
                GameSetup.Vocabulary.VocabularyType.ENUMERATED -> {
                    val charRange = getCharRange(setup.vocabulary.characters)
                    Validators.alphabet(charRange)
                }
            })
        }

        if (occurrences) {
            if (setup.vocabulary.characterOccurrences < setup.vocabulary.length) {
                validators.add(Validators.characterOccurrences(setup.vocabulary.characterOccurrences))
            }
        }

        return when {
            validators.isEmpty() -> Validators.pass()
            validators.size == 1 -> validators[0]
            else -> Validators.all(validators)
        }
    }

    private fun getGenerator(setup: GameSetup, evaluator: Boolean = false): CandidateGenerationModule {
        val guessPolicy = setup.evaluation.enforced
        val solutionPolicy = setup.evaluation.type
        val seed = getRandomSeed(setup)

        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> if (evaluator) {
                // evaluator: use the 99.5% common words as the secret
                VocabularyListGenerator(
                    getWordList(setup.vocabulary.length, WordListType.SECRETS, portion = 0.995f),
                    guessPolicy, solutionPolicy,
                    filter = getValidator(setup, vocabulary = false),
                    seed = seed
                )
            } else {
                // guesser: cascade through expanding word lists as solutions are eliminated
                val validator = getValidator(setup, vocabulary = false)
                CascadingGenerator(
                    product = 50000,
                    solutions = 5,
                    generators = listOf(
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.SECRETS, truncate = 500),
                            guessPolicy,
                            solutionPolicy,
                            filter = validator,
                            seed = seed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.SECRETS),
                            guessPolicy,
                            solutionPolicy,
                            filter = validator,
                            seed = seed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.GUESSES),
                            guessPolicy,
                            solutionPolicy,
                            solutionVocabulary = getWordList(setup.vocabulary.length,
                                WordListType.SECRETS
                            ),
                            filter = validator,
                            seed = seed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.GUESSES),
                            guessPolicy,
                            solutionPolicy,
                            filter = validator,
                            seed = seed
                        )
                    )
                )
            }

            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> if (evaluator) {
                if (setup.evaluator == GameSetup.Evaluator.HONEST) {
                    // enumerating all valid codes is unnecessarily expensive for honest evaluators;
                    // just generate one code and be done with it.
                    OneCodeEnumeratingGenerator(
                        getCharRange(setup.vocabulary.characters),
                        setup.vocabulary.length,
                        maxOccurrences = setup.vocabulary.characterOccurrences,
                        seed = seed
                    )
                } else {
                    // dishonest evaluation requires expensive computations at each step to
                    // generate and examine the solution space. Use truncating generation to ensure
                    // this comparison does not get prohibitively expensive.
                    SolutionTruncatedEnumerationCodeGenerator(
                        getCharRange(setup.vocabulary.characters),
                        setup.vocabulary.length,
                        solutionPolicy,
                        maxOccurrences = setup.vocabulary.characterOccurrences,
                        shuffle = true,
                        truncateAtSize = 10000,
                        pretruncateAtSize = 100000,
                        seed = seed
                    )
                }
            } else {
                // TODO modify to CodeCollapsingEnumerationGenerator for efficiency.
                CodeEnumeratingGenerator(
                    getCharRange(setup.vocabulary.characters),
                    setup.vocabulary.length,
                    guessPolicy,
                    solutionPolicy,
                    maxOccurrences = setup.vocabulary.characterOccurrences,
                    seed = seed
                )
            }
        }
    }

    private fun getCharRange(characters: Int) = 'a'.until('a' + characters)

    private fun getRandomSeed(setup: GameSetup): Long {
        // note: it is possible to replay the same secret with slightly different settings
        // (e.g. changing Feedback policy, changing the length of the code, etc.) by maintaining
        // the "seed core" and changing the detail. This produces a distinct "game seed" that is
        // recognized as a new game, but with the same secret value or a substring of it.
        // In GameSetupManager, seed core and seed detail are considered separately.
        // Ensure distinct game experiences here.
        return setup.randomSeed + when {
            setup.version <= 1 -> 0
            else -> {
                val typeOrdinal = when (setup.evaluation.type) {
                    ConstraintPolicy.AGGREGATED_EXACT -> 1
                    ConstraintPolicy.AGGREGATED_INCLUDED -> 2
                    ConstraintPolicy.AGGREGATED -> 3
                    ConstraintPolicy.POSITIVE,
                    ConstraintPolicy.ALL,
                    ConstraintPolicy.PERFECT -> 4
                    else -> 0
                }
                setup.vocabulary.length * 17
                + setup.vocabulary.characters * 13
                + setup.vocabulary.characterOccurrences * 11
                + typeOrdinal * 7
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Vocabulary File I/O
    //---------------------------------------------------------------------------------------------

    enum class WordListType { SECRETS, GUESSES, ACCEPTABLE, VALID }

    // TODO if adding new vocabulary files, e.g. for different languages, consider cache eviction
    private val cachedWordLists = mutableMapOf<String, List<String>>()

    private fun getWordList(length: Int = 5, type: WordListType, truncate: Int? = null, portion: Float? = null): List<String> {
        val keyBase = "en-US/standard/length-${length}"

        var portionTruncate = 1.0f
        val portionBase = when {
            portion == null -> ""
            portion >= 0.995 -> "-995"
            portion >= 0.99 -> "-99"
            portion >= 0.95 -> "-95"
            portion >= 0.90 -> "-90"
            else -> {
                // approximate
                portionTruncate = portion / 0.90f
                "-90"
            }
        }

        val words = when(type) {
            WordListType.SECRETS -> readWordList("${keyBase}/secrets${portionBase}.txt")
            WordListType.GUESSES -> readWordList("${keyBase}/guesses.txt")
            WordListType.ACCEPTABLE -> readWordList("${keyBase}/acceptable.txt")
            WordListType.VALID -> readWordList("${keyBase}/valid.txt")
        }

        var endIndex = words.size - 1
        if (portionTruncate < 1.0f) {
            val portionIndex = Math.round(endIndex * portionTruncate)
            endIndex = Math.min(endIndex, portionIndex)
        }
        if (truncate != null) {
            endIndex = Math.min(endIndex, truncate - 1)
        }

        return if (endIndex == words.size - 1) words else words.slice(0..endIndex)
    }

    private fun readWordList(assetPath: String): List<String> {
        return getCached(assetPath) { key ->
            assets.open("words/$key").bufferedReader().use { it.readLines() }
        }
    }

    private fun getCached(key: String, loader: (String) -> List<String>): List<String> {
        synchronized(cachedWordLists) {
            if (!cachedWordLists.containsKey(key)) {
                cachedWordLists[key] = loader(key)
            }
            return cachedWordLists[key]!!
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Serialization and Legacy Support
    //---------------------------------------------------------------------------------------------

    private val gson = Gson()

    private enum class LegacyVersion {
        /**
         * Launch version. Did not support vocabulary filters (e.g. "no letter repetitions").
         * This version was not saved wrapped in VersionedSerialization.
         */
        V01,

        /**
         * Add vocabulary filters and different Constraint types, e.g. "no letter repetitions"
         * and "report Included counts only".
         * First use of VersionedSerialization.
         */
        V02;

        companion object {
            val CURRENT = V02
        }
    }

    private data class VersionedSerialization(
        val version: LegacyVersion,
        val serialization: String
    )

    private fun serialize(saveData: GameSaveData): String {
        val versionedSerialization = VersionedSerialization(
            LegacyVersion.CURRENT,
            gson.toJson(saveData)
        )
        return gson.toJson(versionedSerialization)
    }

    private fun deserialize(text: String): GameSaveData {
        val versionedSerialization: VersionedSerialization
        try {
            versionedSerialization = gson.fromJson(text, VersionedSerialization::class.java)
        } catch (error: JsonSyntaxException) {
            // treat as V1
            return deserializeLegacySave(LegacyVersion.V01, text)
        }

        return if (versionedSerialization.version == LegacyVersion.CURRENT) {
            gson.fromJson(versionedSerialization.serialization, GameSaveData::class.java)
        } else {
            deserializeLegacySave(versionedSerialization.version, versionedSerialization.serialization)
        }
    }

    private fun deserializeLegacySave(version: LegacyVersion, serialization: String): GameSaveData = when (version) {
        LegacyVersion.V01 -> convertLegacySave(gson.fromJson(serialization, V01GameSaveData::class.java))
        LegacyVersion.V02 -> gson.fromJson(serialization, GameSaveData::class.java)
    }

    private fun convertLegacySave(legacyData: V01GameSaveData): GameSaveData {
        val legacySetup = legacyData.setup
        val gameSetup = GameSetup(
            board = GameSetup.Board(rounds = legacySetup.board.rounds),
            evaluation = GameSetup.Evaluation(
                type = legacySetup.evaluation.type,
                enforced = legacySetup.evaluation.enforced
            ),
            vocabulary = GameSetup.Vocabulary(
                language = legacySetup.vocabulary.language,
                type = when (legacySetup.vocabulary.type) {
                    V01GameSetup.Vocabulary.VocabularyType.LIST -> GameSetup.Vocabulary.VocabularyType.LIST
                    V01GameSetup.Vocabulary.VocabularyType.ENUMERATED -> GameSetup.Vocabulary.VocabularyType.ENUMERATED
                },
                length = legacySetup.vocabulary.length,
                characters = legacySetup.vocabulary.characters,
                characterOccurrences = legacySetup.vocabulary.length,
                secret = legacySetup.vocabulary.secret
            ),
            solver = when (legacySetup.solver) {
                V01GameSetup.Solver.PLAYER -> GameSetup.Solver.PLAYER
                V01GameSetup.Solver.BOT -> GameSetup.Solver.BOT
            },
            evaluator = when (legacySetup.evaluator) {
                V01GameSetup.Evaluator.PLAYER -> GameSetup.Evaluator.PLAYER
                V01GameSetup.Evaluator.HONEST -> GameSetup.Evaluator.HONEST
                V01GameSetup.Evaluator.CHEATER -> GameSetup.Evaluator.CHEATER
            },
            randomSeed = legacySetup.randomSeed,
            daily = legacySetup.daily,
            version = legacySetup.version
        )

        return GameSaveData(
            seed = legacyData.seed,
            setup = gameSetup,
            settings = legacyData.settings,
            constraints = legacyData.constraints,
            currentGuess = legacyData.currentGuess,
            uuid = legacyData.uuid
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Persistence
    //---------------------------------------------------------------------------------------------
    companion object {
        const val GAME_SAVE_FILENAME = "gameSave.json"
        const val GAME_RECORD_DIRNAME = "gameRecords"
        const val GAME_RECORD_EXT = "json"
    }

    private var lastSaveData: GameSaveData? = null

    private fun getLoadFiles(seed: String?): List<File> {
        val currentFile = File(context.filesDir, GAME_SAVE_FILENAME)
        return if (seed == null) listOf(currentFile) else {
            val saneSeed = seed.replace('/', '_')
            val saneRecord = "$saneSeed.$GAME_RECORD_EXT"
            val recordDir = File(context.filesDir, GAME_RECORD_DIRNAME)
            val recordFile = if (recordDir.isDirectory) File(recordDir, saneRecord) else null
            if (recordFile?.exists() != true) listOf(currentFile) else listOf(
                recordFile,
                currentFile
            )
        }
    }

    private fun getLoadFile(seed: String?): File {
        val currentFile = File(context.filesDir, GAME_SAVE_FILENAME)
        return if (seed == null) currentFile else {
            val saneSeed = seed.replace('/', '_')
            val saneRecord = "$saneSeed.$GAME_RECORD_EXT"
            val recordDir = File(context.filesDir, GAME_RECORD_DIRNAME)
            val recordFile = if (recordDir.isDirectory) File(recordDir, saneRecord) else null
            if (recordFile?.exists() == true) recordFile else currentFile
        }
    }

    private fun getGameState(save: GameSaveData): Game.State {
        return when {
            save.constraints.any { it.correct } -> Game.State.WON
            save.constraints.size == save.settings.rounds -> Game.State.LOST
            save.currentGuess != null -> Game.State.EVALUATING
            else -> Game.State.GUESSING
        }
    }

    private fun getSaveFiles(gameSaveData: GameSaveData): List<File> {
        // val state = getGameState(gameSaveData)
        if (gameSaveData.seed == null) {
            // no seed, or not yet done; only save as current game, not in records
            return listOf(File(context.filesDir, GAME_SAVE_FILENAME))
        }

        // seeded and complete games are also saved in the records
        val saneSeed = gameSaveData.seed.replace('/', '_')
        val saneRecord = "$saneSeed.$GAME_RECORD_EXT"
        val recordDir = File(context.filesDir, GAME_RECORD_DIRNAME)
        if (!recordDir.isDirectory) recordDir.mkdir()

        return listOf(
            File(context.filesDir, GAME_SAVE_FILENAME),
            File(recordDir, saneRecord)
        )
    }

    private fun loadGameSaveData(seed: String?, setup: GameSetup?): GameSaveData? {
        try {
            synchronized(this) {
                if (isGameSaveData(seed, setup, lastSaveData)) return lastSaveData
                val file = getLoadFile(seed)
                Timber.v("loading game file from ${file.absolutePath}")
                if (file.exists()) {
                    val serialized = file.readText()
                    Timber.v("loaded game file; has length ${serialized.length}")
                    val save = deserialize(serialized)
                    if (isGameSaveData(seed, setup, save)) return save
                    Timber.v("rejected game file (no match)")
                }
            }
        } catch (err: Exception) {
            Timber.w(err, "An error occurred loading a persisted game save (seed $seed)")
        }

        return null
    }

    private fun isGameSaveData(seed: String?, setup: GameSetup?, save: GameSaveData?): Boolean {
        return save != null
                && (seed == null || save.seed == seed)
                && (setup == null || save.setup == setup)
    }

    override fun loadState(seed: String?, setup: GameSetup?): Game.State? {
        val save = loadGameSaveData(seed, setup)
        return if (save == null) null else getGameState(save)
    }

    override fun loadSave(seed: String?, setup: GameSetup?): GameSaveData? = loadGameSaveData(seed, setup)

    override fun loadGame(seed: String?, setup: GameSetup?): Pair<GameSaveData, Game>? {
        val save = loadGameSaveData(seed, setup)
        return if (save == null) null else Pair(save, Game.atMove(
            save.settings,
            getValidator(save.setup),
            save.uuid,
            save.constraints,
            save.currentGuess
        ))
    }

    override fun saveGame(seed: String?, setup: GameSetup, game: Game) {
        val gameSaveData = GameSaveData(seed, setup, game)
        saveGame(gameSaveData)
    }

    override fun saveGame(saveData: GameSaveData) {
        val serialized = serialize(saveData)
        synchronized(this) {
            lastSaveData = saveData
            getSaveFiles(saveData).forEach {
                Timber.v("writing game file to ${it.absolutePath}")
                it.writeText(serialized)
            }
        }
    }

    override fun clearSavedGame(seed: String?, setup: GameSetup) {
        try {
            synchronized(this) {
                if (isGameSaveData(seed, setup, lastSaveData)) lastSaveData = null
                getLoadFiles(seed).filter {
                    val serialized = it.readText()
                    val save = deserialize(serialized)
                    isGameSaveData(seed, setup, save)
                }.forEach { it.delete() }
            }
        } catch (err: Exception) {
            Timber.e(err, "An error occurred deleting saved game ${seed}")
        }
    }

    override fun clearSavedGames() {
        try {
            synchronized(this) {
                lastSaveData = null
                File(context.filesDir, GAME_SAVE_FILENAME).delete()
                File(context.filesDir, GAME_RECORD_DIRNAME).deleteRecursively()
            }
        } catch (err: Exception) {
            Timber.e(err, "An error occurred deleting saved games")
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}