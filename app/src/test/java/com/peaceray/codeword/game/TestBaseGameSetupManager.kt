package com.peaceray.codeword.game

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.setup.impl.setup.BaseGameSetupManager
import com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned.seed.SeedCoreFactory
import com.peaceray.codeword.game.bot.modules.generation.enumeration.OneCodeEnumeratingGenerator
import com.peaceray.codeword.game.data.ConstraintPolicy
import org.junit.Assert.*
import org.junit.Test


/**
 * IntHistogram is a utility class used to represent and store (as a string) the player's record
 * of wins and loses for a particular game type. A given histogram is used to hold the history of
 * a particular game outcome on a particular game type, for example solved completions of
 * "Length 5 English Word" puzzles. The keys are number of guesses attempted at the time a game
 * ended, the values are the number of games ended in that many guesses.
 *
 * This class is fairly complicated internally and, though important to the app overall, would
 * present bugs in a way largely unnoticeable during normal app operation (mostly as an incorrect
 * history of wins and loses, requiring complex accounting to verify).
 */
class TestBaseGameSetupManager {

    //region Properties
    //---------------------------------------------------------------------------------------------

    private val seedCoreFactory = SeedCoreFactory()
    private val gameSetupManager = BaseGameSetupManager()

    //---------------------------------------------------------------------------------------------
    //endregion

    //region V1 Seeds
    //---------------------------------------------------------------------------------------------

    private fun createV1GameSetup(language: CodeLanguage, length: Int, characters: Int = 26, seed: String? = null) = GameSetup(
        vocabulary = GameSetup.Vocabulary(
            language,
            type = if (language == CodeLanguage.ENGLISH) GameSetup.Vocabulary.VocabularyType.LIST else GameSetup.Vocabulary.VocabularyType.ENUMERATED,
            length,
            characters,
            length
        ),
        board = GameSetup.Board(6),
        evaluation = GameSetup.Evaluation(
            if (language == CodeLanguage.ENGLISH) ConstraintPolicy.PERFECT else ConstraintPolicy.AGGREGATED,
            ConstraintPolicy.IGNORE
        ),
        solver = GameSetup.Solver.PLAYER,
        evaluator = GameSetup.Evaluator.HONEST,
        randomSeed = if (seed == null) 0L else seedCoreFactory.getRandomSeed(seed.split("/")[0]),
        daily = false,
        version = 1
    )

    private fun createV1DataSet(): Map<GameSetup, List<String>> {
        val map = mutableMapOf<GameSetup, List<String>>()

        // CODE
        map[createV1GameSetup(CodeLanguage.CODE, 5, 6)] = listOf(
            "8M1DY83/V3",
            "vWkKir5/M2",
            "Dj6iyU3/Q",
            "zsVAFo6/9"
        )

        map[createV1GameSetup(CodeLanguage.CODE, 5, 8)] = listOf(
            "zsVAFo6/E2",
            "RQpzwz5/4",
            "xzt8H7/Q3"
        )

        map[createV1GameSetup(CodeLanguage.CODE, 8, 16)] = listOf(
            "xzt8H7/y6",
            "Btpkr/Y8"
        )

        map[createV1GameSetup(CodeLanguage.CODE, 3, 4)] = listOf(
            "Btpkr/J2",
            "Xr1EXD2/f5"
        )

        // ENGLISH
        map[createV1GameSetup(CodeLanguage.ENGLISH, 3)] = listOf(
            "Xr1EXD2/c3",
            "e9znHt3/A3"
        )

        map[createV1GameSetup(CodeLanguage.ENGLISH, 5)] = listOf(
            "e9znHt3/E3",
            "xPKSmX4/s"
        )

        map[createV1GameSetup(CodeLanguage.ENGLISH, 10)] = listOf(
            "eQjVo54/k",
            "cDToy9/w"
        )

        map[createV1GameSetup(CodeLanguage.ENGLISH, 11)] = listOf(
            "VqcWk32/c2",
            "eQjVo54/n"
        )

        map[createV1GameSetup(CodeLanguage.ENGLISH, 12)] = listOf(
            "xPKSmX4/C",
            "VqcWk32/e2"
        )

        return map.toMap()
    }

    private fun assertEquals(expected: GameSetup, actual: GameSetup) {
        assertEquals(expected.vocabulary, actual.vocabulary)
        assertEquals(expected.board, actual.board)
        assertEquals(expected.evaluation, actual.evaluation)
        assertEquals(expected.solver, actual.solver)
        assertEquals(expected.evaluator, actual.evaluator)
        if (expected.randomSeed != 0L) assertEquals(expected.randomSeed, actual.randomSeed)
        assertEquals(expected.daily, actual.daily)
        assertEquals(expected.version, actual.version)
    }

    @Test
    fun getSetup_from_V1Seed() {
        val data = createV1DataSet()

        // check, disregarding random seed
        data.forEach { (setup, seeds) ->
            seeds.forEach { seed -> assertEquals(setup, gameSetupManager.getSetup(seed)) }
        }

        // check setup + randomSeed
        data.forEach { (setup, seeds) ->
            seeds.forEach { seed ->
                val setupWithSeed = setup.with(randomSeed = seedCoreFactory.getRandomSeed(seed.split("/")[0]))
                assertEquals(setupWithSeed, gameSetupManager.getSetup(seed))
            }
        }
    }

    @Test
    fun getSeed_from_V1Setup() {
        val data = createV1DataSet()

        // check setup + randomSeed -> seed
        data.forEach { (setup, seeds) ->
            seeds.forEach { seed ->
                val setupWithSeed = setup.with(randomSeed = seedCoreFactory.getRandomSeed(seed.split("/")[0]))
                val expectedSeed = gameSetupManager.getSeed(setupWithSeed)
                assertEquals(expectedSeed, seed)
            }
        }

        // check seed -> setup -> seed
        val seeds = data.values.flatten()
        seeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val reseed = gameSetupManager.getSeed(setup)
            assertEquals(seed, reseed)
        }
    }

    @Test
    fun getSetup_from_V1Seed_OneCodeEnumeratingGenerator() {
        val setup = gameSetupManager.getSetup("cDToy9/i3")
        val generator = OneCodeEnumeratingGenerator('A'..'F', 5, 5, setup.randomSeed)
        val code = generator.generateCandidates().solutions.first()

        assertEquals("CCFCD", code)
    }

    @Test
    fun modifyGameSetup_keepVersion() {
        val data = createV1DataSet()
        val seeds = data.values.flatten()

        // check for new length
        seeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val length = setup.vocabulary.length + if (setup.vocabulary.length > 6) -1 else 1
            val vocabulary = GameSetup.Vocabulary(
                setup.vocabulary.language,
                setup.vocabulary.type,
                length,
                setup.vocabulary.characters,
                length
            )
            val expected = setup.with(
                vocabulary = vocabulary
            )
            val actual = gameSetupManager.modifyGameSetup(setup, vocabulary = vocabulary)

            assertEquals(expected, actual)
        }

        // check for evaluation enforced
        val englishSeeds = data.filter { it.key.vocabulary.language == CodeLanguage.ENGLISH }.values.flatten()
        englishSeeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val evaluation = GameSetup.Evaluation(
                ConstraintPolicy.PERFECT,
                ConstraintPolicy.POSITIVE
            )
            val expected = setup.with(
                evaluation = evaluation
            )
            val actual = gameSetupManager.modifyGameSetup(setup, evaluation = evaluation)

            assertEquals(expected, actual)
        }
    }

    @Test
    fun modifyGameSetup_upgradeVersion() {
        val data = createV1DataSet()
        val seeds = data.values.flatten()

        // check for maximum 1 character repetition
        seeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val vocabulary = GameSetup.Vocabulary(
                setup.vocabulary.language,
                setup.vocabulary.type,
                setup.vocabulary.length,
                setup.vocabulary.characters,
                1
            )
            val expected = setup.with(
                vocabulary = vocabulary,
                version = seedCoreFactory.getSeedVersionInteger()
            )
            val actual = gameSetupManager.modifyGameSetup(setup, vocabulary = vocabulary)

            assertEquals(expected, actual)
        }

        // check for evaluation type
        seeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val evaluation = GameSetup.Evaluation(
                ConstraintPolicy.AGGREGATED_INCLUDED,
                ConstraintPolicy.IGNORE
            )
            val expected = setup.with(
                evaluation = evaluation,
                version = seedCoreFactory.getSeedVersionInteger()
            )
            val actual = gameSetupManager.modifyGameSetup(setup, evaluation = evaluation)

            assertEquals(expected, actual)
        }

        // randomize the seed
        seeds.forEach { seed ->
            val setup = gameSetupManager.getSetup(seed)
            val actual = gameSetupManager.modifyGameSetup(setup, randomized = true)
            val expected = setup.with(
                randomSeed = actual.randomSeed,
                version = seedCoreFactory.getSeedVersionInteger()
            )

            assertEquals(expected, actual)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}