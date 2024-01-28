package com.peaceray.codeword.domain.manager.game.impl.setup.versioned

import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.VersionedBySeed

/**
 * A [GameTypeFactory] specifically for Seeded games. An abstract class; concrete implementations
 * should apply versioned game rule logic. This class provides a companion function to select
 * the appropriate [GameTypeFactory] for a given [SeedVersion].
 */
abstract class SeededGameTypeFactory(version: SeedVersion): GameTypeFactory, VersionedBySeed(version) {
    companion object {
        private val cachedFactories: MutableMap<SeedVersion, SeededGameTypeFactory> = mutableMapOf()
        private val defaultFactory = SeededGameTypeFactoryV2()

        fun getFactory(seedVersion: SeedVersion): SeededGameTypeFactory {
            var factory = cachedFactories[seedVersion]

            if (factory == null) {
                factory = when (seedVersion) {
                    SeedVersion.V1 -> SeededGameTypeFactoryV1()
                    SeedVersion.V2 -> SeededGameTypeFactoryV2()
                }
                cachedFactories[seedVersion] = factory
            }

            return factory
        }

        fun getGameType(seedVersion: SeedVersion, randomSeed: Long, seedDetail: String): GameType = getFactory(seedVersion).getGameType(randomSeed, seedDetail)

        fun getSeedDetail(seedVersion: SeedVersion, randomSeed: Long, gameType: GameType): String = getFactory(seedVersion).getSeedDetail(randomSeed, gameType)

        fun generateSeedDetail(seedVersion: SeedVersion, randomSeed: Long): String = getFactory(seedVersion).generateSeedDetail(randomSeed)
    }
}