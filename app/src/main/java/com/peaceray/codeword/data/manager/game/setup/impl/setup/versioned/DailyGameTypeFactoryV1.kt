package com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.random.ConsistentRandom
import java.lang.IllegalArgumentException

/**
 * This is a versioned game rule class! Do not make ANY modifications to the behavior of this class
 * after [SeedVersion.V1] launches. To alter game rules in the future, create a new class for the
 * new [SeedVersion].
 *
 * Generates Daily GameTypes using the game's randomSeed to select between available modes.
 * In this version, the Daily may be an English word of 3-6 letters, or a Code sequence of 3-5.
 */
internal class DailyGameTypeFactoryV1: DailyGameTypeFactory(SeedVersion.V1) {
    override fun getGameType(randomSeed: Long, seedDetail: String): GameType {
        if (seedDetail.isNotBlank()) {
            throw IllegalArgumentException("seedDetail must be blank for Dailies")
        }

        // dailies can be any of the following:
        // (3..6) letter English words
        // code sequences of length (3..5) w/ 8, 6, and 6 characters respectively.
        // try for five word puzzles and two code puzzles a week: about 70% chance of word.
        // Emphasize 4 and 5 letter words, with 3 and 6 less likely.

        // consistently "random" across environments
        val random = ConsistentRandom(randomSeed)
        // throw out the first value; it's used to select the puzzle solution. Basically impossible
        // to cheat and determine the solution based on game type but it's cheap to generate them
        // from different values. And it feels better.
        random.nextInt()

        // pick a game mode
        val a = random.nextFloat()
        return when {
            a < 0.10 -> GameType(CodeLanguage.ENGLISH, 3, 26, 3, ConstraintPolicy.PERFECT)
            a < 0.35 -> GameType(CodeLanguage.ENGLISH, 4, 26, 4, ConstraintPolicy.PERFECT)
            a < 0.60 -> GameType(CodeLanguage.ENGLISH, 5, 26, 5, ConstraintPolicy.PERFECT)
            a < 0.70 -> GameType(CodeLanguage.ENGLISH, 6, 26, 6, ConstraintPolicy.PERFECT)
            a < 0.80 -> GameType(CodeLanguage.CODE, 3, 8, 3, ConstraintPolicy.AGGREGATED)
            a < 0.90 -> GameType(CodeLanguage.CODE, 4, 6, 4, ConstraintPolicy.AGGREGATED)
            else -> GameType(CodeLanguage.CODE, 5, 6, 5, ConstraintPolicy.AGGREGATED)
        }
    }
}
