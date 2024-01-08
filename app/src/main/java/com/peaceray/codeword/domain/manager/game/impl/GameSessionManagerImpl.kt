package com.peaceray.codeword.domain.manager.game.impl

import android.content.Context
import android.content.res.AssetManager
import androidx.core.util.rangeTo
import com.google.gson.Gson
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameSessionManager
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
import com.peaceray.codeword.game.data.Settings
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
    //-----------------------------------------------------------------------------------------
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
                val commonWords = getWordList(setup.vocabulary.length, WordListType.SECRETS, portion = 0.9f).toHashSet()
                val notRareWords = getWordList(setup.vocabulary.length, WordListType.SECRETS, portion = 0.99f).toHashSet()
                val threshold = botSettingsManager.solverStrength.toDouble()
                val solutionBias = 1 - botSettingsManager.solverStrength.toDouble()
                ModularSolver(
                    getGenerator(setup),
                    InformationGainScorer(setup.evaluation.type) { if (it in commonWords) 100.0 else if (it in notRareWords) 10.0 else 1.0 },
                    StochasticThresholdScoreSelector(threshold, solutionBias, seed = setup.randomSeed)   // TODO difficulty
                )
            }
            else -> throw IllegalArgumentException("Can't create a solver of type ${setup.solver}")
        }
    }

    override fun getEvaluator(setup: GameSetup): Evaluator {
        // TODO caching

        // Explicit Secret?
        if (setup.vocabulary.secret != null) {
            return ModularHonestEvaluator(
                OneCodeGenerator(setup.vocabulary.secret),
                UnitScorer(),
                RandomSelector(solutions = true, seed = setup.randomSeed)
            )
        }

        return when(setup.evaluator) {
            GameSetup.Evaluator.HONEST -> ModularHonestEvaluator(
                getGenerator(setup, evaluator = true),
                UnitScorer(),
                RandomSelector(solutions = true, seed = setup.randomSeed)
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

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Persistence
    //-----------------------------------------------------------------------------------------
    companion object {
        const val GAME_SAVE_FILENAME = "gameSave.json"
        const val GAME_RECORD_DIRNAME = "gameRecords"
        const val GAME_RECORD_EXT = "json"
        val gson = Gson()
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
                    val save = gson.fromJson(serialized, GameSaveData::class.java)
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
        val serialized = gson.toJson(saveData)
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
                    val save = gson.fromJson(serialized, GameSaveData::class.java)
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

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Intermediate Object Creation
    //-----------------------------------------------------------------------------------------
    private fun getValidator(setup: GameSetup) = when(setup.vocabulary.type) {
        GameSetup.Vocabulary.VocabularyType.LIST -> Validators.words(
            getWordList(setup.vocabulary.length, WordListType.VALID)
        )
        GameSetup.Vocabulary.VocabularyType.ENUMERATED -> Validators.alphabet(
            getCharRange(setup.vocabulary.characters)
        )
    }

    private fun getGenerator(setup: GameSetup, evaluator: Boolean = false): CandidateGenerationModule {
        val guessPolicy = setup.evaluation.enforced
        val solutionPolicy = setup.evaluation.type

        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> if (evaluator) {
                // evaluator: use the 99.5% common words as the secret
                VocabularyListGenerator(
                    getWordList(setup.vocabulary.length, WordListType.SECRETS, portion = 0.995f),
                    guessPolicy, solutionPolicy,
                    seed = setup.randomSeed
                )
            } else {
                // guesser: cascade through expanding word lists as solutions are eliminated
                CascadingGenerator(
                    product = 50000,
                    solutions = 5,
                    generators = listOf(
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.SECRETS, truncate = 500),
                            guessPolicy,
                            solutionPolicy,
                            seed = setup.randomSeed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.SECRETS),
                            guessPolicy,
                            solutionPolicy,
                            seed = setup.randomSeed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.GUESSES),
                            guessPolicy,
                            solutionPolicy,
                            solutionVocabulary = getWordList(setup.vocabulary.length, WordListType.SECRETS),
                            seed = setup.randomSeed
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.vocabulary.length, WordListType.GUESSES),
                            guessPolicy,
                            solutionPolicy,
                            seed = setup.randomSeed
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
                        seed = setup.randomSeed
                    )
                } else {
                    // dishonest evaluation requires expensive computations at each step to
                    // generate and examine the solution space. Use truncating generation to ensure
                    // this comparison does not get prohibitively expensive.
                    SolutionTruncatedEnumerationCodeGenerator(
                        getCharRange(setup.vocabulary.characters),
                        setup.vocabulary.length,
                        solutionPolicy,
                        shuffle = true,
                        truncateAtSize = 10000,
                        pretruncateAtSize = 100000,
                        seed = setup.randomSeed
                    )
                }
            } else {
                // TODO modify to CodeCollapsingEnumerationGenerator for efficiency.
                CodeEnumeratingGenerator(
                    getCharRange(setup.vocabulary.characters),
                    setup.vocabulary.length,
                    guessPolicy,
                    solutionPolicy,
                    seed = setup.randomSeed
                )
            }
        }
    }

    private fun getCharRange(characters: Int) = 'a'.until('a' + characters)

    //-----------------------------------------------------------------------------------------
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
}