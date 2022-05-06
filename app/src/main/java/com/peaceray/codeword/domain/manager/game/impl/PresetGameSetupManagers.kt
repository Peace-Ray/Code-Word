package com.peaceray.codeword.domain.manager.game.impl

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.data.model.code.toCodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.utils.extensions.fromFakeB58
import com.peaceray.codeword.utils.extensions.toFakeB58
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.random.Random

abstract class GameSetupManagerImpl: GameSetupManager {
    private val languageDetails = mapOf(
        CodeLanguage.ENGLISH to CodeLanguageDetails(
            language = CodeLanguage.ENGLISH,
            evaluation = ConstraintPolicy.ALL,
            hardModeConstraint = ConstraintPolicy.POSITIVE,
            characters = ('a'..'z').toList(),
            isEnumeration = false,
            codeLengthRecommended = 5,
            codeCharactersRecommended = 26,
            (3..12).toList(),
            listOf(26),
            true
        ),
        CodeLanguage.CODE to CodeLanguageDetails(
            language = CodeLanguage.CODE,
            evaluation = ConstraintPolicy.AGGREGATED,
            hardModeConstraint = null,
            characters = ('a'..'p').toList(),
            isEnumeration = true,
            codeLengthRecommended = 4,
            codeCharactersRecommended = 6,
            (4..8).toList(),
            (6..16).toList(),
            false
        )
    )

    private fun getGameSetupVocabulary(seedVocabParts: List<String>): GameSetup.Vocabulary {
        val languageDetails = languageDetails[seedVocabParts[0].toCodeLanguage()]!!
        val vocabType = if (languageDetails.isEnumeration) {
            GameSetup.Vocabulary.VocabularyType.ENUMERATED
        } else {
            GameSetup.Vocabulary.VocabularyType.LIST
        }
        val seedVocabLength = if (seedVocabParts.size <= 1 || seedVocabParts[1].isBlank()) {
            languageDetails.codeLengthRecommended
        } else {
            val seedCodeLength = seedVocabParts[1].toInt(10)
            if (seedCodeLength !in languageDetails.codeLengthsSupported) {
                throw IllegalArgumentException("Illegal 'code length' number in seed vocabulary $seedVocabParts")
            }
            seedCodeLength
        }
        val seedVocabChars = if (seedVocabParts.size >= 3) seedVocabParts[2].toInt() else languageDetails.codeCharactersRecommended

        if (seedVocabChars !in languageDetails.codeCharactersSupported) {
            throw IllegalArgumentException("Illegal 'code character' number in seed vocab $seedVocabParts")
        }

        return GameSetup.Vocabulary(languageDetails.language, vocabType, seedVocabLength, seedVocabChars)
    }

    private fun getGameSetupBoard(seedVocabParts: List<String>, vocabulary: GameSetup.Vocabulary): GameSetup.Board {
        // TODO consider storing round count in seed?
        return GameSetup.Board(6)
    }

    override fun getDailySetup(hard: Boolean): GameSetup {
        // TODO revise this date to actual launch time
        val dateStr = "2/1/2022"
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH)
        val startDate = sdf.parse(dateStr)!!

        val dateNow = Date()

        val diff = dateNow.time - startDate.time
        val days = if (diff < 0) 0 else TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)

        // use seed of #days
        return getSetup("#${days}", hard)
    }

    override fun getSetup(seed: String, hard: Boolean): GameSetup {
        val parts = seed.trim().split("/", limit = 2)
        val seedPart = parts[0]

        val daily = seedPart.firstOrNull() == '#'
        val seedValue = getRandomSeed(seedPart)

        val vocabStr = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "en.5"
        val vocabParts = vocabStr.split(".", limit = 3)

        val vocabulary = getGameSetupVocabulary(vocabParts)
        val board = getGameSetupBoard(vocabParts, vocabulary)
        val language = languageDetails[vocabulary.language]!!

        val hardModeConstraint = language.hardModeConstraint ?: ConstraintPolicy.IGNORE
        val evaluation = GameSetup.Evaluation(
            language.evaluation,
            if (hard) hardModeConstraint else ConstraintPolicy.IGNORE
        )

        return GameSetup(
            board,
            evaluation,
            vocabulary,
            solver = GameSetup.Solver.PLAYER,
            evaluator = GameSetup.Evaluator.HONEST,
            randomSeed = seedValue,
            daily
        )
    }

    override fun getSeed(setup: GameSetup): String? {
        // Must have human solver, honest bot evaluator
        if (setup.solver != GameSetup.Solver.PLAYER || setup.evaluator != GameSetup.Evaluator.HONEST) {
            return null
        }

        return listOf(
            getSeedPrefix(setup),
            getSeedVocab(setup)
        ).filter { it.isNotEmpty() }.joinToString("/")
    }

    private fun getRandomSeed(seed: String? = null): Long {
        val seedPrefix = seed?.split("/", limit = 2)?.get(0)
        return when {
            seedPrefix == null || seedPrefix.isEmpty() -> Random.nextInt().toLong()
            seedPrefix[0] == '#' -> {
                val num = seed.substring(1).toInt(10)
                (Int.MAX_VALUE.toLong() + 1) * num + num
            }
            else -> seedPrefix.fromFakeB58()
        }
    }

    private fun getSeedPrefix(gameSetup: GameSetup): String {
        return when {
            gameSetup.daily -> "#${gameSetup.randomSeed / (Int.MAX_VALUE.toLong() + 1)}"
            else -> gameSetup.randomSeed.toFakeB58()
        }
    }

    private fun getSeedVocab(setup: GameSetup): String {
        val language = setup.vocabulary.language
        val wordLength = setup.vocabulary.length
        val characters = setup.vocabulary.characters

        val details = languageDetails[language]!!

        val parts = mutableListOf(language.abbreviation, wordLength.toString(), characters.toString())

        // three parts: language, code length, # characters.
        // given the first 'n', defaults may be assumed for the rest;
        // if defaults match actual, omit the remaining

        // remove the last?
        if (language != CodeLanguage.ENGLISH || wordLength != details.codeLengthRecommended) {
            // must include first two; possibly omit last
            if (characters == details.codeCharactersRecommended) parts.removeLast()
        } else if (language != CodeLanguage.ENGLISH) {
            // might remove second and third
            if (characters == details.codeCharactersRecommended) {
                parts.removeLast()
                parts.removeLast()
            }
        } else {
            // might omit all three
            if (characters == details.codeCharactersRecommended) {
                parts.removeLast()
                if (wordLength == details.codeLengthRecommended) {
                    parts.clear()
                }
            }
        }

        return parts.joinToString(".")
    }

    override fun modifyGameSetup(
        setup: GameSetup,
        board: GameSetup.Board?,
        evaluation: GameSetup.Evaluation?,
        vocabulary: GameSetup.Vocabulary?,
        solver: GameSetup.Solver?,
        evaluator: GameSetup.Evaluator?,
        hard: Boolean?,
        seed: String?,
        language: CodeLanguage?
    ): GameSetup {
        var candidate = setup
        val lDeets = languageDetails[candidate.vocabulary.language]!!
        var candidateIsHard = candidate.evaluation.enforced != ConstraintPolicy.IGNORE

        // seed: load from seed, recur to apply other modifications.
        if (seed != null) {
            val makeHard = hard ?: (candidate.evaluation.enforced != ConstraintPolicy.IGNORE)
            candidate = getSetup(seed = seed, hard = makeHard)
            return modifyGameSetup(candidate, board, evaluation, vocabulary, solver, evaluator)
        }

        // vocabulary / language: can change evaluation and board width
        val newVocabulary = if (language == null) vocabulary else {
            val newDeets = languageDetails[language]!!
            val vocabType = if (newDeets.isEnumeration) {
                GameSetup.Vocabulary.VocabularyType.ENUMERATED
            } else {
                GameSetup.Vocabulary.VocabularyType.LIST
            }
            
            val vocabLength = when {
                vocabulary != null -> vocabulary.length
                candidate.vocabulary.length in newDeets.codeLengthsSupported -> candidate.vocabulary.length
                else -> newDeets.codeLengthRecommended
            }

            val vocabChars = when {
                vocabulary != null -> vocabulary.characters
                candidate.vocabulary.characters in newDeets.codeCharactersSupported -> candidate.vocabulary.characters
                else -> newDeets.codeCharactersRecommended
            }

            GameSetup.Vocabulary(language, vocabType, vocabLength, vocabChars)
        }

        if (newVocabulary != null) {
            // vocabularies can change evaluation type and board width
            val newLanguage = languageDetails[newVocabulary.language]!!
            val hardModeConstraint = newLanguage.hardModeConstraint ?: ConstraintPolicy.IGNORE
            val preferredHard = hard ?: candidateIsHard
            
            val newEvaluation = GameSetup.Evaluation(
                newLanguage.evaluation,
                if (preferredHard) hardModeConstraint else ConstraintPolicy.IGNORE
            )

            // seed is already used. We've just consumed vocabulary, board, evaluation, and "hard".
            // recur with the remaining settings.
            return modifyGameSetup(
                setup = candidate.with(vocabulary = newVocabulary, evaluation = newEvaluation),
                board = board,
                solver = solver,
                evaluator = evaluator
            )
        }

        // evaluator can be freely changed, but if the evaluator becomes dishonest (from any other
        // setting), the number of rounds is set to 0. Similarly, if it becomes honest, the number
        // of rounds is set to non-zero.
        if (evaluator != null) {
            var newBoard = candidate.board
            if (evaluator != candidate.evaluator) {
                if (evaluator == GameSetup.Evaluator.CHEATER) {
                    // became cheater
                    newBoard = GameSetup.Board(0)
                } else if (candidate.evaluator == GameSetup.Evaluator.CHEATER && candidate.board.rounds == 0) {
                    // became non-cheater
                    // TODO consider default game rounds?
                    newBoard = GameSetup.Board(6)
                }
            }
            candidate = candidate.with(board = newBoard, evaluator = evaluator)
        }

        // changing evaluation, solver, and board are straightforward with no side-effects
        // (potentially they produce invalid setups but that is checked for later).
        if (board != null || evaluation != null || solver != null) {
            candidate = candidate.with(
                board = board,
                evaluation = evaluation,
                solver = solver
            )
        }

        // apply hard mode (a stronger modification than "evaluation")
        if (hard != null) {
            val hardModeConstraint = lDeets.hardModeConstraint ?: ConstraintPolicy.IGNORE
            candidate = candidate.with(evaluation = GameSetup.Evaluation(
                candidate.evaluation.type,
                if (hard) hardModeConstraint else ConstraintPolicy.IGNORE
            ))
        }

        // check "candidate" for internal consistency.
        if (candidate.daily) {
            // only English 5
            if (candidate.vocabulary.language != CodeLanguage.ENGLISH || candidate.vocabulary.length != lDeets.codeLengthRecommended) {
                throw UnsupportedOperationException("Daily must use en.5 vocabulary")
            }

            // only human player
            if (candidate.evaluator != GameSetup.Evaluator.HONEST || candidate.solver != GameSetup.Solver.PLAYER) {
                throw UnsupportedOperationException("Daily must have human guesser and honest evaluation")
            }
        }

        // board width must be supported
        if (candidate.vocabulary.length !in lDeets.codeLengthsSupported) {
            throw UnsupportedOperationException("Board width outside supported language widths")
        }

        // vocab char set must be supported
        if (candidate.vocabulary.characters !in lDeets.codeCharactersSupported) {
            throw UnsupportedOperationException("Vocabulary characters outside supported character sets")
        }

        // check evaluation for language support
        if (candidate.evaluation.type != lDeets.evaluation) {
            throw UnsupportedOperationException("Evaluation type not supported by language")
        }

        if (candidate.evaluation.enforced != ConstraintPolicy.IGNORE && candidate.evaluation.enforced != lDeets.hardModeConstraint) {
            throw UnsupportedOperationException("Evaluation enforcement (hard mode) not supported by language")
        }

        return candidate
    }

    override fun getCodeLanguageDetails(language: CodeLanguage) = languageDetails[language]!!

    override fun isHard(setup: GameSetup): Boolean {
        return setup.evaluation.enforced != ConstraintPolicy.IGNORE
    }
}

class WordPuzzleGameSetupManager @Inject constructor(): GameSetupManagerImpl() {
    override fun getSetup(hard: Boolean): GameSetup {
        return GameSetup.forWordPuzzle(honest = true, hard = hard)
    }
}

class WordEvaluationGameSetupManager @Inject constructor(): GameSetupManagerImpl() {
    override fun getSetup(hard: Boolean): GameSetup {
        return GameSetup.forWordEvaluation(hard)
    }
}

class WordDemoGameSetupManager @Inject constructor(): GameSetupManagerImpl() {
    override fun getSetup(hard: Boolean): GameSetup {
        return GameSetup.forWordDemo(honest = true, hard = hard)
    }
}