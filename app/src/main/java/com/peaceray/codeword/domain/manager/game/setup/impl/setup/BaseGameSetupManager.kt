package com.peaceray.codeword.domain.manager.game.setup.impl.setup

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.domain.manager.game.setup.GameSetupManager
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.DailyGameTypeFactory
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.GameTypeFactory
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.SeededGameTypeFactory
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.language.CodeLanguageDetailsFactory
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.seed.SeedCoreFactory
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.game.data.ConstraintPolicy
import timber.log.Timber
import java.lang.Exception
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A GameSetupManager implementation that delegates [SeedVersion]-specific logic and game rules
 * to versioned component classes, accessed through Factories based on the game's encoded
 * version.
 *
 * Hopefully, most game rule modifications and extensions can be implemented w/in versioned component
 * classes and their respective abstract accessor class, but take care to verify that any changes
 * added to those classes do not require modification of this class to function -- and if so,
 * that that modification does not impair the ability to process legacy strings appropriately.
 */
@Singleton
class BaseGameSetupManager @Inject constructor(): GameSetupManager {

    private val seedCoreFactory = SeedCoreFactory()

    //region Validation / Versioning
    //---------------------------------------------------------------------------------------------

    override fun getSeedEra(seed: String): GameSetupManager.SeedEra {
        val currentVersion = seedCoreFactory.getSeedVersionInteger()
        val seedVersion = try {
            val seedCore = seed.trim().split("/", limit = 2)[0]
            seedCoreFactory.getSeedVersionInteger(seedCore)
        } catch (e: Exception) {
            Timber.e(e, "seed $seed")
            return GameSetupManager.SeedEra.UNKNOWN
        }

        // When versions are retired, check for them here.

        return when {
            seedVersion == currentVersion -> GameSetupManager.SeedEra.CURRENT
            seedVersion < currentVersion && SeedVersion.isNumberEncoding(seedVersion) ->
                GameSetupManager.SeedEra.LEGACY
            seedVersion < currentVersion -> GameSetupManager.SeedEra.UNKNOWN
            else -> GameSetupManager.SeedEra.FUTURISTIC
        }
    }

    override fun getSeedEra(gameSetup: GameSetup): GameSetupManager.SeedEra {
        val currentVersion = seedCoreFactory.getSeedVersionInteger()

        // When versions are retired, check for them here.

        return when {
            gameSetup.version == currentVersion -> GameSetupManager.SeedEra.CURRENT
            gameSetup.version < currentVersion && SeedVersion.isNumberEncoding(gameSetup.version) ->
                GameSetupManager.SeedEra.LEGACY
            gameSetup.version < currentVersion -> GameSetupManager.SeedEra.UNKNOWN
            else -> GameSetupManager.SeedEra.FUTURISTIC
        }
    }

    override fun getSeedVersion(): Int = seedCoreFactory.getSeedVersionInteger()

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Creating GameSetups
    //---------------------------------------------------------------------------------------------
    override fun getSetup(hard: Boolean): GameSetup {
        val seedCore = seedCoreFactory.generateSeedCore()
        val randomSeed = seedCoreFactory.getRandomSeed(seedCore)
        val seedVersion = seedCoreFactory.getSeedVersion(seedCore)
        val seedDetail = SeededGameTypeFactory.generateSeedDetail(seedVersion, randomSeed)
        val seed = listOf(seedCore, seedDetail).filter { it.isNotBlank() }.joinToString("/")
        return getSetup(seed, hard)
    }

    override fun getDailySetup(hard: Boolean): GameSetup {
        val seedCore = seedCoreFactory.generateDailySeedCore()
        val randomSeed = seedCoreFactory.getRandomSeed(seedCore)
        val seedVersion = seedCoreFactory.getSeedVersion(seedCore)
        val seedDetail = DailyGameTypeFactory.generateSeedDetail(seedVersion, randomSeed)
        val seed = listOf(seedCore, seedDetail).filter { it.isNotBlank() }.joinToString("/")
        return getSetup(seed, hard)
    }

    override fun getSetup(seed: String, hard: Boolean): GameSetup {
        val parts = seed.trim().split("/", limit = 2)
        val seedCore = parts[0]
        val seedDetail = parts.getOrNull(1) ?: ""

        val seedVersion = seedCoreFactory.getSeedVersion(seedCore)
        val randomSeed = seedCoreFactory.getRandomSeed(seedCore)
        val isDaily = seedCoreFactory.isDaily(seedCore)

        val gameTypeFactory: GameTypeFactory = if (isDaily) {
            DailyGameTypeFactory.getFactory(seedVersion)
        } else {
            SeededGameTypeFactory.getFactory(seedVersion)
        }

        val gameType = gameTypeFactory.getGameType(randomSeed, seedDetail)

        return buildSetup(
            seedVersion = seedVersion,
            randomSeed = randomSeed,
            gameType = gameType,
            daily = isDaily,
            hard = hard
        )
    }

    override fun getSetup(type: GameType, hard: Boolean) = buildSetup(
        seedVersion = seedCoreFactory.getSeedVersion(),
        randomSeed = seedCoreFactory.getRandomSeed(),
        gameType = type,
        daily = false,
        hard = hard
    )
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameSetup Build Helpers
    //---------------------------------------------------------------------------------------------
    private fun buildSetup(
        seedVersion: SeedVersion,
        randomSeed: Long,
        gameType: GameType,
        daily: Boolean,
        hard: Boolean,
    ): GameSetup {
        val details = CodeLanguageDetailsFactory.get(seedVersion, gameType.language)
        val vocabType = when {
            details.isEnumeration -> GameSetup.Vocabulary.VocabularyType.ENUMERATED
            else -> GameSetup.Vocabulary.VocabularyType.LIST
        }
        val constraintPolicy = gameType.feedback
        val hardModeConstraint = details.hardModeConstraint[gameType.feedback] ?: ConstraintPolicy.IGNORE

        val vocabulary = GameSetup.Vocabulary(gameType.language, vocabType, gameType.length, gameType.characters, gameType.characterOccurrences)
        val evaluation = GameSetup.Evaluation(
            constraintPolicy,
            if (hard) hardModeConstraint else ConstraintPolicy.IGNORE
        )
        val board = GameSetup.Board(getRecommendedRounds(vocabulary, evaluation).first)


        return GameSetup(
            board,
            evaluation,
            vocabulary,
            solver = GameSetup.Solver.PLAYER,
            evaluator = GameSetup.Evaluator.HONEST,
            randomSeed = randomSeed,
            daily = daily,
            version = seedVersion.numberEncoding
        )
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Examining GameSetups
    //---------------------------------------------------------------------------------------------

    override fun getSeed(setup: GameSetup): String? {
        // Must have human solver, honest bot evaluator
        if (setup.solver != GameSetup.Solver.PLAYER || setup.evaluator != GameSetup.Evaluator.HONEST) {
            return null
        }

        // Must have a supported seed version
        val seedVersion = try {
            SeedVersion.forNumberEncoding(setup.version)
        } catch (e: Exception) {
            return null
        }

        val gameType = getType(setup)
        val gameTypeFactory: GameTypeFactory = if (setup.daily) {
            DailyGameTypeFactory.getFactory(seedVersion)
        } else {
            SeededGameTypeFactory.getFactory(seedVersion)
        }

        val seedCore = seedCoreFactory.getSeedCore(setup)
        val seedDetail = gameTypeFactory.getSeedDetail(setup.randomSeed, gameType)

        return listOf(seedCore, seedDetail).filter { it.isNotEmpty() }.joinToString("/")
    }

    override fun isHard(setup: GameSetup): Boolean {
        return setup.evaluation.enforced != ConstraintPolicy.IGNORE
    }

    override fun getType(setup: GameSetup): GameType {
        val details = CodeLanguageDetailsFactory.get(
            SeedVersion.forNumberEncoding(setup.version),
            setup.vocabulary.language
        )

        // verify language validity
        if (setup.vocabulary.length !in details.codeLengthsSupported) {
            throw IllegalArgumentException("Illegal 'code length' number in game setup $setup")
        } else if (setup.vocabulary.characters !in details.codeCharactersSupported) {
            throw IllegalArgumentException("Illegal 'code characters' number in game setup $setup")
        } else if (setup.vocabulary.characters * setup.vocabulary.characterOccurrences < setup.vocabulary.length) {
            throw java.lang.IllegalArgumentException("Illegal 'character occurrences' number in game setup $setup")
        }

        return GameType(
            setup.vocabulary.language,
            setup.vocabulary.length,
            setup.vocabulary.characters,
            setup.vocabulary.characterOccurrences,
            setup.evaluation.type
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Code Language Setup
    //---------------------------------------------------------------------------------------------

    /**
     * Retrieve CodeLanguage details for the provided language, including recommendations for
     * code lengths and character counts.
     */
    override fun getCodeLanguageDetails(language: CodeLanguage): CodeLanguageDetails {
        return CodeLanguageDetailsFactory.get(seedCoreFactory.getSeedVersion(), language)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Difficulty Setup
    //---------------------------------------------------------------------------------------------

    /**
     * Retrieve recommendations for the number of rounds a gome with this vocabulary should be played.
     *
     * @param vocabulary The game Vocabulary
     * @return A 2-tuple of the recommended rounds, and the recommended maximum number of rounds.
     */
    override fun getRecommendedRounds(vocabulary: GameSetup.Vocabulary, evaluation: GameSetup.Evaluation?): Pair<Int, Int> {
        val extraRounds = when {
            evaluation == null -> 0
            evaluation.type.isByLetter() -> 0
            evaluation.type == ConstraintPolicy.AGGREGATED -> 2
            else -> 4
        }

        fun asPair(rounds: Int, maxRounds: Int) = Pair(rounds + extraRounds, maxRounds + extraRounds)

        return when (vocabulary.language) {
            CodeLanguage.ENGLISH -> asPair(6, 8)
            CodeLanguage.CODE -> asPair(6, 10)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Game Modification
    //---------------------------------------------------------------------------------------------

    override fun modifyGameSetup(
        setup: GameSetup,
        board: GameSetup.Board?,
        evaluation: GameSetup.Evaluation?,
        vocabulary: GameSetup.Vocabulary?,
        solver: GameSetup.Solver?,
        evaluator: GameSetup.Evaluator?,
        hard: Boolean?,
        seed: String?,
        language: CodeLanguage?,
        randomized: Boolean?
    ): GameSetup {
        var candidate = setup
        val defaultVersion = seedCoreFactory.getSeedVersion()
        val candidateIsHard = candidate.evaluation.enforced != ConstraintPolicy.IGNORE

        val getLanguageDetails: (l: CodeLanguage, update: Boolean) -> CodeLanguageDetails = { l, update ->
            if (update && !CodeLanguageDetailsFactory.has(SeedVersion.forNumberEncoding(candidate.version), l)) {
                candidate = candidate.with(version = defaultVersion.numberEncoding)
            }
            CodeLanguageDetailsFactory.get(SeedVersion.forNumberEncoding(candidate.version), l)
        }

        // seed: generate from seed, recur to apply other modifications.
        if (seed != null) {
            val makeHard = hard ?: (candidate.evaluation.enforced != ConstraintPolicy.IGNORE)
            candidate = getSetup(seed = seed, hard = makeHard)
            return modifyGameSetup(candidate, board, evaluation, vocabulary, solver, evaluator)
        }

        // known-null: "seed"

        // create a vocabulary from the specified language, if any
        val newVocabulary = if (language == null) vocabulary else {
            // new language: update version if necessary
            val newDetails = getLanguageDetails(language, true)

            val vocabType = if (newDetails.isEnumeration) {
                GameSetup.Vocabulary.VocabularyType.ENUMERATED
            } else {
                GameSetup.Vocabulary.VocabularyType.LIST
            }

            val vocabLength = when {
                vocabulary != null -> vocabulary.length
                candidate.vocabulary.length in newDetails.codeLengthsSupported -> candidate.vocabulary.length
                else -> newDetails.codeLengthRecommended
            }

            val vocabChars = when {
                vocabulary != null -> vocabulary.characters
                candidate.vocabulary.characters in newDetails.codeCharactersSupported -> candidate.vocabulary.characters
                else -> newDetails.codeCharactersRecommended
            }

            val repetitionsSupported = newDetails.codeCharacterRepetitionsSupported.map { if (it == 0) candidate.vocabulary.length else it }
            val charOccurrences = when {
                vocabulary != null -> vocabulary.characterOccurrences
                candidate.vocabulary.characterOccurrences in repetitionsSupported -> candidate.vocabulary.characterOccurrences
                else -> repetitionsSupported.first()
            }

            GameSetup.Vocabulary(language, vocabType, vocabLength, vocabChars, charOccurrences)
        }

        // apply any new vocabulary (provided or inferred from language).
        // the rest will be handled in a recursive call.
        if (newVocabulary != null) {
            // new vocabulary; update version if necessary
            val newDetails = getLanguageDetails(newVocabulary.language, true)

            // update evaluation, with more urgency if the Language has changed.
            val languageChanged = candidate.vocabulary.language != newVocabulary.language
            val candidateEvaluationSupported = candidate.evaluation.type in newDetails.evaluationsSupported
            val evaluationPolicy = if (languageChanged || !candidateEvaluationSupported) {
                newDetails.evaluationRecommended
            } else {
                candidate.evaluation.type
            }
            val hardModeConstraint = newDetails.hardModeConstraint[evaluationPolicy] ?: ConstraintPolicy.IGNORE
            val preferredHard = hard ?: candidateIsHard

            val newEvaluation = GameSetup.Evaluation(
                evaluationPolicy,
                if (preferredHard) hardModeConstraint else ConstraintPolicy.IGNORE
            )

            // seed is already used. We've just consumed vocabulary and hard; seed is known-null.
            // recur with the remaining settings.
            return modifyGameSetup(
                setup = candidate.with(
                    vocabulary = newVocabulary,
                    evaluation = newEvaluation
                ),
                board = board,
                evaluation = evaluation,
                solver = solver,
                evaluator = evaluator,
                randomized = randomized
            )
        }

        // known-null: "seed", "language", "vocabulary"

        // changing evaluator, evaluation, solver, and board are straightforward with no side-effects
        // (potentially they produce invalid setups but that is checked for later).
        if (board != null || evaluation != null || solver != null || evaluator != null) {
            candidate = candidate.with(
                board = board,
                evaluation = evaluation,
                solver = solver,
                evaluator = evaluator
            )
        }

        // apply hard mode (a stronger modification than "evaluation")
        if (hard != null) {
            val lDetails = getLanguageDetails(setup.vocabulary.language, true)
            val hardModeConstraint = lDetails.hardModeConstraint[candidate.evaluation.type] ?: ConstraintPolicy.IGNORE
            candidate = candidate.with(evaluation = GameSetup.Evaluation(
                candidate.evaluation.type,
                if (hard) hardModeConstraint else ConstraintPolicy.IGNORE
            ))
        }

        // randomize seed
        if (randomized == true) {
            if (candidate.daily) {
                throw UnsupportedOperationException("Daily cannot be randomized")
            }

            // randomize the seed and update the version to current
            candidate = candidate.with(randomized = true, version = defaultVersion.numberEncoding)
        }

        // check "candidate" for internal consistency.
        // allow one-time version update to the current default, if necessary to pass a
        // validity check.
        val performCheck: (() -> Unit) -> Unit = { check ->
            if (candidate.version == defaultVersion.numberEncoding) check() else {
                // check: if throws, upgrade and check again
                try {
                    check()
                } catch (e: UnsupportedOperationException) {
                    candidate = candidate.with(version = defaultVersion.numberEncoding)
                    check()
                }
            }
        }

        performCheck {
            if (candidate.daily) {
                // only human player
                if (candidate.evaluator != GameSetup.Evaluator.HONEST || candidate.solver != GameSetup.Solver.PLAYER) {
                    throw UnsupportedOperationException("Daily must have human guesser and honest evaluation")
                }
            }

            val lDetails = getLanguageDetails(candidate.vocabulary.language, false)

            // board width must be supported
            if (candidate.vocabulary.length !in lDetails.codeLengthsSupported) {
                throw UnsupportedOperationException("Board width outside supported language widths")
            }

            // vocab char set must be supported
            if (candidate.vocabulary.characters !in lDetails.codeCharactersSupported) {
                throw UnsupportedOperationException("Vocabulary characters outside supported character sets")
            }

            // character occurrences must be supported by the language
            val repetitionsSupported = lDetails.codeCharacterRepetitionsSupported.map { if (it == 0) candidate.vocabulary.length else it }
            if (candidate.vocabulary.characterOccurrences !in repetitionsSupported) {
                throw UnsupportedOperationException("Vocabulary character occurrences not supported by language")
            }

            // character occurrences must be possible given length and characters
            if (candidate.vocabulary.characters * candidate.vocabulary.characterOccurrences < candidate.vocabulary.length) {
                throw UnsupportedOperationException("Vocabulary character occurrences allow no words")
            }

            // check evaluation for language support
            if (candidate.evaluation.type !in lDetails.evaluationsSupported) {
                throw UnsupportedOperationException("Evaluation type not supported by language")
            }

            if (candidate.evaluation.enforced != ConstraintPolicy.IGNORE && candidate.evaluation.enforced != lDetails.hardModeConstraint[candidate.evaluation.type]) {
                throw UnsupportedOperationException("Evaluation enforcement (hard mode) not supported by language")
            }
        }

        return candidate
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}