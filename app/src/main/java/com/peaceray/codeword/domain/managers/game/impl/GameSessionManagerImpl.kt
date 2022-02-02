package com.peaceray.codeword.domain.managers.game.impl

import android.content.res.AssetManager
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.managers.game.GameSessionManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.*
import com.peaceray.codeword.game.bot.modules.generation.*
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumInvertedScorer
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.MinimumScoreSelector
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.bot.modules.selection.StochasticThresholdScoreSelector
import com.peaceray.codeword.game.data.Settings
import com.peaceray.codeword.game.validators.AlphabetValidator
import com.peaceray.codeword.game.validators.VocabularyValidator
import com.peaceray.codeword.glue.ForApplication
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameSessionManagerImpl @Inject constructor(
    @ForApplication val assets: AssetManager
): GameSessionManager {
    override fun createGame(setup: GameSetup): Game {
        return Game(
            settings = Settings(
                setup.board.letters,
                if (setup.board.rounds > 0) setup.board.rounds else 100000,
                setup.evaluation.enforced
            ),
            validator = when(setup.vocabulary.type) {
                GameSetup.Vocabulary.VocabularyType.LIST -> VocabularyValidator(
                    getWordList(setup.board.letters, WordListSize.FULL)
                )
                GameSetup.Vocabulary.VocabularyType.ENUMERATED -> AlphabetValidator(
                    getCharRange(setup.vocabulary.characters)
                )
            }
        )
    }

    override fun getSolver(setup: GameSetup): Solver {
        // TODO caching

        return when(setup.solver) {
            GameSetup.Solver.BOT -> {
                val commonWords = getWordList(setup.board.letters).toHashSet()
                ModularSolver(
                    getGenerator(setup),
                    InformationGainScorer(setup.evaluation.type) { if (it in commonWords) 100.0 else 1.0 },
                    StochasticThresholdScoreSelector(threshold = 0.7, solutionBias = 0.3)   // TODO difficulty
                )
            }
            else -> throw IllegalArgumentException("Can't create a solver of type ${setup.solver}")
        }
    }

    override fun getEvaluator(setup: GameSetup): Evaluator {
        // TODO caching

        return when(setup.evaluator) {
            GameSetup.Evaluator.HONEST -> ModularHonestEvaluator(
                getGenerator(setup, evaluator = true),
                UnitScorer(),
                RandomSelector(solutions = true)
            )
            GameSetup.Evaluator.CHEATER -> ModularFlexibleEvaluator(
                getGenerator(setup, evaluator = true),
                KnuthMinimumInvertedScorer(setup.evaluation.type),
                MinimumScoreSelector(solutions = true)
            )
            else -> throw IllegalArgumentException("Can't create an evaluator of type ${setup.evaluator}")
        }
    }

    override fun getCodeCharacters(setup: GameSetup): Iterable<Char> {
        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> getCharRange(26)
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> getCharRange(setup.vocabulary.characters)
        }
    }

    private fun getGenerator(setup: GameSetup, evaluator: Boolean = false): CandidateGenerationModule {
        val guessPolicy = setup.evaluation.enforced
        val solutionPolicy = setup.evaluation.type

        return when(setup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> if (evaluator) {
                VocabularyListGenerator(getWordList(setup.board.letters), guessPolicy, solutionPolicy)
            } else {
                CascadingGenerator(
                    product = 50000,
                    solutions = 5,
                    generators = listOf(
                        VocabularyListGenerator(getWordList(setup.board.letters, WordListSize.SPARSE), guessPolicy, solutionPolicy),
                        VocabularyListGenerator(getWordList(setup.board.letters), guessPolicy, solutionPolicy),
                        VocabularyListGenerator(
                            getWordList(setup.board.letters, WordListSize.FULL),
                            guessPolicy,
                            solutionPolicy,
                            solutionVocabulary = getWordList(setup.board.letters)
                        ),
                        VocabularyListGenerator(
                            getWordList(setup.board.letters, WordListSize.FULL),
                            guessPolicy,
                            solutionPolicy
                        )
                    )
                )
            }

            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> CodeEnumeratingGenerator(
                getCharRange(setup.vocabulary.characters),
                setup.board.letters,
                guessPolicy,
                solutionPolicy
            )
        }
    }

    private fun getCharRange(characters: Int) = 'a'.until('a' + characters)

    //region Vocabulary Files
    //---------------------------------------------------------------------------------------------
    enum class WordListSize { SPARSE, STANDARD, FULL }

    // TODO if adding new vocabulary files, e.g. for different languages, consider cache eviction
    private val cachedWordLists = mutableMapOf<String, List<String>>()

    private fun getWordList(length: Int = 5, size: WordListSize = WordListSize.STANDARD): List<String> {
        val keyBase = "en-US/${length}"

        return when(size) {
            WordListSize.SPARSE -> readWordList("${keyBase}_0.txt")
            WordListSize.STANDARD -> readWordList("${keyBase}_1.txt")
            WordListSize.FULL -> getCached("${keyBase}_full") {
                listOf(
                    readWordList("${keyBase}_1.txt"),
                    readWordList("${keyBase}_2.txt")
                ).flatten()
                    .distinct()
            }
        }
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