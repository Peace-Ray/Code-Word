package com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.utils.extensions.fromFakeB58
import com.peaceray.codeword.utils.extensions.toFakeB58
import java.lang.IllegalArgumentException
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow

/**
 * [SeedCoreFactory] provides a canonical place to convert versioned game seeds between string
 * representation and component parts. Note that for the most part, we do not expect internal rules to change,
 * as the SeedCore stores only the random seed value and the rule version ([SeedVersion]). Other
 * components should take care to consider how rule sets for game construction make change as a
 * result of the [SeedVersion].
 *
 * The primary role of SeedCoreFactory is to convert between a string-encoded game seed and the
 * tuple of (randomSeed, seedVersion) it encodes. For Seeded games, both values are readable from
 * the seed itself, using a 0-padded concatenation. SeedVersion provides [SeedVersion.numberEncoding]
 * which has large mutual Hamming distances and a zero-free base-10 representation for this purpose.
 *
 * For Daily games, the "seed" is just the Daily count: "#1", "#143", etc. The SeedCoreFactory
 * will generate (reversibly) the appropriate randomSeed and seedVersion from this number based
 * on "switchover dates". When new rules for Daily puzzles are implemented, they will be scheduled
 * for specific dates, represented as a Daily count in the future. Users must have updated their
 * app at or before that date to play the daily (this is enforced elsewhere, using a web query
 * or other method to verify the minimum app release required).
 */
class SeedCoreFactory {

    // If changed, alter TestSeedCoreFactory to compensate.
    private val defaultVersion = SeedVersion.V2

    // Do not change these after launch without building in compensation for legacy values!
    private val firstDaily: Calendar = Calendar.getInstance()
    private val dailyRandomSeedA = 51202493L
    private val dailyRandomSeedB = 487145950223L

    init {
        // Remember that months begin at 0 = January
        firstDaily.set(2024, 0, 22)     // January 22, 2024
    }

    private fun seedVersionFirstDaily(seedVersion: SeedVersion): Calendar = when (seedVersion) {
        SeedVersion.V1 -> firstDaily
        SeedVersion.V2 -> { // February 12th, 2024
            val calendar = Calendar.getInstance()
            calendar.set(2024, 1, 12)
            calendar
        }
    }

    fun isDaily(seedCore: String? = null) = seedCore != null && seedCore.firstOrNull() == '#'

    fun getSeedVersionInteger(seedCore: String? = null): Int {
        if (seedCore == null) {
            return defaultVersion.numberEncoding
        }

        return if (isDaily(seedCore)) {
            // daily. The rest is the daily number.
            // As of this writing, all dailies follow the same rules.
            // Future updates will apply consistent versioning to all dailies below a switchover
            // date, then switch to the new version. Some other component should check a
            // canonical reference (e.g. an external API) to determine if an app update is required
            // for the "Daily" feature to function.
            val moment = getDailyDay(seedCore).timeInMillis
            SeedVersion.values().sortedByDescending { it.numberEncoding }.first {
                moment >= seedVersionFirstDaily(it).timeInMillis
            }.numberEncoding
        } else {
            val parts = decode(seedCore)
            parts.second
        }
    }

    fun getSeedVersion(seedCore: String? = null) = SeedVersion.forNumberEncoding(getSeedVersionInteger(seedCore))

    fun getRandomSeed(seedCore: String? = null): Long {
        if (seedCore == null) {
            return GameSetup.createSeed()
        }

        return if (isDaily(seedCore)) {
            // daily. The rest is the daily number.
            // As of this writing, all dailies follow the same rules.
            // Future updates will apply consistent versioning to all dailies below a switchover
            // date, then switch to the new version. Some other component should check a
            // canonical reference (e.g. an external API) to determine if an app update is required
            // for the "Daily" feature to function.
            val dailyCount = seedCore.substring(1).toInt(10)
            dailyRandomSeedA * dailyCount + dailyRandomSeedB
        } else {
            decode(seedCore).first
        }
    }

    fun getSeedCore(setup: GameSetup) = if (setup.daily) {
        val dailyCount = (setup.randomSeed - dailyRandomSeedB) / dailyRandomSeedA
        "#${dailyCount.toInt()}"
    } else {
        encode(setup.randomSeed, setup.version)
    }

    fun getDailySeedCore(day: Calendar): String {
        val diff = day.timeInMillis - firstDaily.timeInMillis
        val days = if (diff < 0) 0 else TimeUnit.DAYS.convert(diff, TimeUnit.MILLISECONDS)
        return "#${days + 1}"   // first daily is #1, not #0
    }

    private fun getDailyDay(seedCore: String): Calendar {
        val days = seedCore.substring(1).toLong() - 1    // first daily is #1, not #0
        val diff = TimeUnit.MILLISECONDS.convert(days, TimeUnit.DAYS)
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = firstDaily.timeInMillis + diff
        return calendar
    }

    fun generateSeedCore() = encode(GameSetup.createSeed(), defaultVersion.numberEncoding)

    fun generateDailySeedCore() = getDailySeedCore(Calendar.getInstance())

    private companion object Encoder: SeedFactoryEncoder()
}

/**
 * An Encoder used to convert strings to numbers and numbers to strings. Exposed to allow unit
 * testing of its functionality; it is not intended to be used outside of the [SeedCoreFactory] class.
 */
abstract class SeedFactoryEncoder {
    internal fun encode(seedParts: Pair<Long, Int>): String = encode(seedParts.first, seedParts.second)

    internal fun encode(prefix: Long, suffix: Int): String {
        // prefix must be non-negative
        if (prefix < 0) {
            throw IllegalArgumentException("Encoded prefix must be non-negative")
        }

        // suffix must be non-negative
        if (suffix < 0) {
            throw IllegalArgumentException("Encoded suffix must be non-negative")
        }

        // suffix, if non-zero, must not contain 0s in base-10 representation
        if (suffix > 0 && suffix.toString(10).contains('0')) {
            throw IllegalArgumentException("Encoded suffix must not contain 0s")
        }

        val encodedLong: Long = if (suffix == 0) prefix * 10 else {
            prefix * ((10.0).pow(suffix.digits() + 1)).toLong() + suffix
        }
        return encodedLong.toFakeB58()
    }

    internal fun decode(seed: String): Pair<Long, Int> {
        val encodedLong = seed.fromFakeB58()
        // the lowest-place '0' indicates the break point between the encoded randomSeed and
        // encoded version. Find the position (numbered from 0 = '1's place, 1 = '10's place, etc.)
        val position = (0..encodedLong.digits()).first {
            val magnitude = (10.0).pow(it).toLong()
            // magnitude is 1, 10, 100, 1000, etc.
            // divide by this value and take mod(10) to isolate the digit in that position

            (encodedLong / magnitude) % 10L == 0L
        }

        // position indicates the location of zero.
        val encodedRandomSeed = encodedLong / (10.0).pow(position + 1).toLong()
        val encodedVersion = encodedLong % (10.0).pow(position + 1).toLong()

        return Pair(encodedRandomSeed, encodedVersion.toInt())
    }

    private fun Int.digits() = when (this) {
        0 -> 1
        else -> log10(abs(toDouble())).toInt() + 1
    }

    private fun Long.digits() = when (this) {
        0L -> 1
        else -> log10(abs(toDouble())).toInt() + 1
    }
}