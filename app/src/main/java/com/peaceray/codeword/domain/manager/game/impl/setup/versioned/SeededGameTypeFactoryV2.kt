package com.peaceray.codeword.domain.manager.game.impl.setup.versioned

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.language.CodeLanguageDetailsFactory
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.random.ConsistentRandom
import com.peaceray.codeword.utils.extensions.fromFakeB58
import com.peaceray.codeword.utils.extensions.toFakeB58

/**
 * This is a versioned game rule class! Do not make ANY modifications to the behavior of this class
 * after [SeedVersion.V1] launches. To alter game rules in the future, create a new class for the
 * new [SeedVersion].
 *
 * Generates Seeded GameTypes using the game's seedDetail to specify parameters. In this version,
 * games may use English words or Code sequences.
 */
internal class SeededGameTypeFactoryV2: SeededGameTypeFactory(SeedVersion.V2) {
    private val languageDetailsFactory: CodeLanguageDetailsFactory by lazy {
        CodeLanguageDetailsFactory.getFactory(seedVersion)
    }

    private fun futz(randomSeed: Long, number: Long): Long {
        return number xor ((randomSeed / 32) % 256)
    }

    override fun getGameType(randomSeed: Long, seedDetail: String): GameType {
        // decode and unfutz
        var detailNumber = futz(randomSeed, seedDetail.fromFakeB58())

        val extract: (Int) -> Int = { size ->
            val value = detailNumber % size
            detailNumber /= size
            value.toInt()
        }

        // an encoded series of indices, with more fundamental features in lower positions.
        // language, then length, then characters.
        val languageIndex = extract(languageDetailsFactory.languages.size)
        val language = languageDetailsFactory.languages.toList()[languageIndex]
        val details = languageDetailsFactory.get(language)

        val lengthIndex = extract(details.codeLengthsSupported.size)
        val charsIndex = extract(details.codeCharactersSupported.size)
        val hasRepetitionAsInt = extract(2)

        val constraintIndex = extract(details.evaluationsSupported.size)

        if (detailNumber != 1L) {
            throw IllegalArgumentException("seedDetail not valid")
        }

        val length = details.codeLengthsSupported[lengthIndex]
        val characters = details.codeCharactersSupported[charsIndex]
        val constraint = details.evaluationsSupported[constraintIndex]

        return GameType(
            language,
            length,
            characters,
            if (hasRepetitionAsInt != 0) length else 1,
            constraint
        )
    }

    override fun getSeedDetail(randomSeed: Long, gameType: GameType): String {
        val details = languageDetailsFactory.get(gameType.language)

        // represent as a series of indices into ordered lists, as tuples: index and size.
        // use an order that allows the next tuple to be determined based on previous tuples;
        // e.g. since "language" determines valid "lengths" and "characters", language must go first.
        val tuples = listOf(
            Pair(languageDetailsFactory.languages.indexOf(gameType.language), languageDetailsFactory.languages.size),
            Pair(details.codeLengthsSupported.indexOf(gameType.length), details.codeLengthsSupported.size),
            Pair(details.codeCharactersSupported.indexOf(gameType.characters), details.codeCharactersSupported.size),
            Pair(if (gameType.characterOccurrences == 1) 0 else 1, 2),
            Pair(details.evaluationsSupported.indexOf(gameType.feedback), details.evaluationsSupported.size)
        )

        // now encode as a number, with more fundamental features appearing in lower positions.
        var total: Long = 1
        for (tuple in tuples.reversed()) {
            // make space for this value in lower-bits, then add it in
            total = total * tuple.second + tuple.first
        }

        // futz some bits and encode
        return futz(randomSeed, total).toFakeB58()
    }

    override fun generateSeedDetail(randomSeed: Long): String {
        // simple start: an English word puzzle of 3..6 letters.

        // consistently "random" across environments
        val random = ConsistentRandom(randomSeed)

        // throw out the first value; it's used to select the puzzle solution. Basically impossible
        // to cheat and determine the solution based on game type but it's cheap to generate them
        // from different values. And it feels better.
        random.nextInt()

        // pick a game mode
        val a = random.nextFloat()
        val gameType = when {
            a < 0.20 -> GameType(CodeLanguage.ENGLISH, 3, 26, 3, ConstraintPolicy.PERFECT)
            a < 0.50 -> GameType(CodeLanguage.ENGLISH, 4, 26, 4, ConstraintPolicy.PERFECT)
            a < 0.80 -> GameType(CodeLanguage.ENGLISH, 5, 26, 5, ConstraintPolicy.PERFECT)
            else -> GameType(CodeLanguage.ENGLISH, 6, 26, 6, ConstraintPolicy.PERFECT)
        }

        return getSeedDetail(randomSeed, gameType)
    }
}