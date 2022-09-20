package com.peaceray.codeword.domain.manager.game.impl.setup.versioned

import com.peaceray.codeword.data.model.game.GameType

/**
 * A GameTypeFactory creates and returns a GameType object based on that game's randomSeed and
 * seedDetail. Note that seedVersion is not explicitly used, which means it must be implicitly
 * specified by the use of a particular GameTypeFactory instance.
 *
 * Individual Factories are free to ignore one or both input values, though the output should always
 * be deterministic given both parameters and the factory class. For instance, a factory for Seeded
 * games will probably ignore the randomSeed value and instead encode GameType in its seedDetail
 * string. A factory for Daily games probably does the reverse: ignoring seedDetail and generating
 * the appropriate GameType deterministically from the randomSeed.
 */
interface GameTypeFactory {

    fun getGameType(randomSeed: Long, seedDetail: String): GameType

    fun getSeedDetail(randomSeed: Long, gameType: GameType): String

    fun generateSeedDetail(randomSeed: Long): String

}