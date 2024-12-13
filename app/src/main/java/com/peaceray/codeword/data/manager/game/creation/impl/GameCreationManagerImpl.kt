package com.peaceray.codeword.data.manager.game.creation.impl

import android.content.Context
import android.content.res.AssetManager
import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.persistence.GamePersistenceManager
import com.peaceray.codeword.data.manager.settings.BotSettingsManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.ModularFlexibleEvaluator
import com.peaceray.codeword.game.bot.ModularHonestEvaluator
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.bot.modules.generation.CascadingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.CodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.OneCodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.SolutionTruncatedEnumerationCodeGenerator
import com.peaceray.codeword.game.bot.modules.generation.vocabulary.OneCodeGenerator
import com.peaceray.codeword.game.bot.modules.generation.vocabulary.VocabularyListGenerator
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
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.glue.ForLocalIO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameCreationManagerImpl @Inject constructor(
    @ForApplication val context: Context,
    @ForApplication val assets: AssetManager,
    @ForComputation private val computationDispatcher: CoroutineDispatcher,
    @ForLocalIO private val ioDispatcher: CoroutineDispatcher,
    private val gamePersistenceManager: GamePersistenceManager,
    private val botSettingsManager: BotSettingsManager
): GameCreationManager {

    //region Game Creation
    //-----------------------------------------------------------------------------------------

    override suspend fun createGame(setup: GameSetup): Game {
        return Game(getSettings(setup), getValidator(setup))
    }

    override suspend fun getGame(seed: String?, setup: GameSetup): Game {
        // load a save; may dispatch for IO
        val save = gamePersistenceManager.load(setup, seed)
        // create a game; may dispatch for IO and/or computation
        return if (save != null) getGame(save) else createGame(setup)
    }

    override suspend fun getGame(save: GameSaveData): Game {
        // create validator; may use its own coroutine context
        val validator = getValidator(save.setup)

        // advance the game to the saved move w/in a computation context
        return withContext(computationDispatcher) {
            Game.atMove(
                save.settings,
                getValidator(save.setup),
                save.uuid,
                save.constraints,
                save.currentGuess
            )
        }
    }

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Examination
    //-----------------------------------------------------------------------------------------

    override fun getSettings(setup: GameSetup) = Settings(
        letters = setup.vocabulary.length,
        rounds = if (setup.board.rounds > 0) setup.board.rounds else 100000,
        constraintPolicy = setup.evaluation.enforced
    )

    override fun getCodeCharacters(setup: GameSetup): Iterable<Char> {
        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> getCharRange(26)
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> getCharRange(setup.vocabulary.characters)
        }
    }

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Game Players
    //-----------------------------------------------------------------------------------------

    override suspend fun getSolver(setup: GameSetup): Solver {
        // TODO caching

        return when(setup.solver) {
            GameSetup.Solver.BOT -> {
                var weight: (String) -> Double = { 1.0 }
                if (setup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
                    val commonWords = getWordList(setup.vocabulary.length,
                        WordListType.SECRETS, portion = 0.9f).toHashSet()
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

    override suspend fun getEvaluator(setup: GameSetup): Evaluator {
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

    //-----------------------------------------------------------------------------------------
    //endregion

    //region Intermediate Object Creation
    //---------------------------------------------------------------------------------------------
    private suspend fun getValidator(setup: GameSetup, vocabulary: Boolean = true, occurrences: Boolean = true): Validator {
        val validators = mutableListOf<Validator>()

        if (vocabulary) {
            validators.add(when (setup.vocabulary.type) {
                GameSetup.Vocabulary.VocabularyType.LIST -> {
                    val wordList = getWordList(setup.vocabulary.length, WordListType.DICTIONARY)
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

    private suspend fun getGenerator(setup: GameSetup, evaluator: Boolean = false): CandidateGenerationModule {
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

    enum class WordListType { SECRETS, GUESSES, ACCEPTABLE, DICTIONARY }

    // TODO if adding new vocabulary files, e.g. for different languages, consider cache eviction
    private val cachedWordLists = mutableMapOf<String, List<String>>()
    private val cachedWordListMutex = Mutex()

    private suspend fun getWordList(length: Int = 5, type: WordListType, truncate: Int? = null, portion: Float? = null): List<String> {
        val dirBase = "en-US/standard"
        val lenBase = "length-${length}"

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
            WordListType.SECRETS -> readWordList("${dirBase}/${lenBase}/secrets${portionBase}.txt")
            WordListType.GUESSES -> readWordList("${dirBase}/${lenBase}/guesses.txt")
            WordListType.ACCEPTABLE -> readWordList("${dirBase}/${lenBase}/acceptable.txt")
            WordListType.DICTIONARY -> readWordList("${dirBase}/dictionary.txt").filter { it.length == length }
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

    private suspend fun readWordList(assetPath: String): List<String> {
        cachedWordListMutex.withLock {
            if (!cachedWordLists.containsKey(assetPath)) {
                cachedWordLists[assetPath] = withContext(ioDispatcher) {
                    assets.open("words/$assetPath").bufferedReader().use { it.readLines() }
                }
            }
            return cachedWordLists[assetPath]!!
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}