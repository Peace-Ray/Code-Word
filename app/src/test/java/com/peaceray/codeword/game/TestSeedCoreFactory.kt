package com.peaceray.codeword.game

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.SeedCoreFactory
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.SeedFactoryEncoder
import com.peaceray.codeword.domain.manager.game.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.random.ConsistentRandom
import com.peaceray.codeword.utils.extensions.toFakeB58
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import kotlin.math.max


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
class TestSeedCoreFactory {

    //region SeedFactoryEncoder
    //---------------------------------------------------------------------------------------------

    private val encoder = object: SeedFactoryEncoder() {}

    private fun assertEncode(encoded: Long, prefix: Long, suffix: Int) {
        assertEncode(encoded.toFakeB58(), prefix, suffix)
    }

    private fun assertEncode(encoded: String, prefix: Long, suffix: Int) {
        assertEquals(encoded, encoder.encode(prefix, suffix))
        assertEquals(encoded, encoder.encode(Pair(prefix, suffix)))
    }

    private fun assertDecode(encoded: Long, prefix: Long, suffix: Int) {
        assertDecode(encoded.toFakeB58(), prefix, suffix)
    }
    
    private fun assertDecode(encoded: String, prefix: Long, suffix: Int) {
        val decoded = encoder.decode(encoded)
        assertEquals(prefix, decoded.first)
        assertEquals(suffix, decoded.second)
    }

    //region SeedFactoryEncoder: Encode: Invalid Arguments
    //---------------------------------------------------------------------------------------------

    @Test
    fun encoder_encode_pair_prefixNegative() {
        // SeedFactoryEncoder will not encode negative prefixes
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(-1, 0))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(-1, 1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(-177 * (Int.MAX_VALUE.toLong() + 1) - 177, 1))
        }
    }

    @Test
    fun encoder_encode_pair_suffixNegative() {
        // SeedFactoryEncoder will not encode negative suffixes
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(0, -1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(1934, -1))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(177 * (Int.MAX_VALUE.toLong() + 1) + 177, -1))
        }
    }

    @Test
    fun encoder_encode_pair_suffixHasZeros() {
        // SeedFactoryEncoder will not encode positive suffixes that contain 0s in base-10
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(0, 10))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(1934, 9303))
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(Pair(177 * (Int.MAX_VALUE.toLong() + 1) + 177, 10))
        }
    }

    @Test
    fun encoder_encode_params_prefixNegative() {
        // SeedFactoryEncoder will not encode negative prefixes
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(-1, 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(-1, 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(-177 * (Int.MAX_VALUE.toLong() + 1) - 177, 1)
        }
    }

    @Test
    fun encoder_encode_params_suffixNegative() {
        // SeedFactoryEncoder will not encode negative suffixes
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(0, -1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(1934, -1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(177 * (Int.MAX_VALUE.toLong() + 1) + 177, -1)
        }
    }

    @Test
    fun encoder_encode_params_suffixHasZeros() {
        // SeedFactoryEncoder will not encode positive suffixes that contain 0s in base-10
        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(0, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(1934, 9303)
        }

        assertThrows(IllegalArgumentException::class.java) {
            encoder.encode(177 * (Int.MAX_VALUE.toLong() + 1) + 177, 10)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedFactoryEncoder: Encode: Valid Arguments
    //---------------------------------------------------------------------------------------------

    @Test
    fun encoder_encode_zeros() {
        assertEncode(0, 0, 0)
        assertEncode(1, 0, 1)
        assertEncode(10, 1, 0)

        assertEncode(12, 0, 12)
        assertEncode(123, 0, 123)
        assertEncode(178, 0, 178)
        assertEncode(93276, 0, 93276)
        assertEncode(9542878, 0, 9542878)

        assertEncode(80, 8, 0)
        assertEncode(9010, 901, 0)
        assertEncode(76200, 7620, 0)
        assertEncode(777770, 77777, 0)
        assertEncode(100000, 10000, 0)
    }

    @Test
    fun encoder_encode_ones() {
        assertEncode(1012, 1, 12)
        assertEncode(10123, 1, 123)
        assertEncode(10178, 1, 178)
        assertEncode(1093276, 1, 93276)
        assertEncode(109542878, 1, 9542878)

        assertEncode(801, 8, 1)
        assertEncode(90101, 901, 1)
        assertEncode(762001, 7620, 1)
        assertEncode(7777701, 77777, 1)
        assertEncode(1000001, 10000, 1)
    }

    @Test
    fun encoder_encode_numbers() {
        assertEncode(8012, 8, 12)
        assertEncode(9010123, 901, 123)
        assertEncode(76200178, 7620, 178)
        assertEncode(77777093276, 77777, 93276)
        assertEncode(1000009542878, 10000, 9542878)
    }

    @Test
    fun encoder_encode_fakeB58Strings() {
        assertEncode("9o3", 8, 12)
        assertEncode("XobN", 901, 123)
        assertEncode("bExJ7", 7620, 178)
        assertEncode("Q7WTu33", 77777, 93276)
        assertEncode("Jhiqzgs", 10000, 9542878)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedFactoryEncoder: Decode
    //---------------------------------------------------------------------------------------------

    @Test
    fun encoder_decode_zeros() {
        assertDecode(0, 0, 0)
        assertDecode(1, 0, 1)
        assertDecode(10, 1, 0)

        assertDecode(12, 0, 12)
        assertDecode(123, 0, 123)
        assertDecode(178, 0, 178)
        assertDecode(93276, 0, 93276)
        assertDecode(9542878, 0, 9542878)

        assertDecode(80, 8, 0)
        assertDecode(9010, 901, 0)
        assertDecode(76200, 7620, 0)
        assertDecode(777770, 77777, 0)
        assertDecode(100000, 10000, 0)
    }

    @Test
    fun encoder_decode_ones() {
        assertDecode(1012, 1, 12)
        assertDecode(10123, 1, 123)
        assertDecode(10178, 1, 178)
        assertDecode(1093276, 1, 93276)
        assertDecode(109542878, 1, 9542878)

        assertDecode(801, 8, 1)
        assertDecode(90101, 901, 1)
        assertDecode(762001, 7620, 1)
        assertDecode(7777701, 77777, 1)
        assertDecode(1000001, 10000, 1)
    }

    @Test
    fun encoder_decode_numbers() {
        assertDecode(8012, 8, 12)
        assertDecode(9010123, 901, 123)
        assertDecode(76200178, 7620, 178)
        assertDecode(77777093276, 77777, 93276)
        assertDecode(1000009542878, 10000, 9542878)
    }

    @Test
    fun encoder_decode_fakeB58Strings() {
        assertDecode("9o3", 8, 12)
        assertDecode("XobN", 901, 123)
        assertDecode("bExJ7", 7620, 178)
        assertDecode("Q7WTu33", 77777, 93276)
        assertDecode("Jhiqzgs", 10000, 9542878)
    }
    
    //---------------------------------------------------------------------------------------------
    //endregion
    
    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory
    //---------------------------------------------------------------------------------------------

    private val factory = SeedCoreFactory()

    // note: update this value to match changes in SeedCoreFactory
    private val defaultVersion = SeedVersion.V1

    // do not change after launch w/o building compensation for legacy values
    private val firstDaily: Calendar = Calendar.getInstance()
    private val dailyRandomSeedA = 51202493L
    private val dailyRandomSeedB = 487145950223L

    init {
        // Remember that months begin at 0 = January
        firstDaily.set(2023, 10, 1)     // November 1, 2023
    }

    //region SeedCoreFactory: Test Helpers
    //---------------------------------------------------------------------------------------------

    private fun dailyNumberToRandomSeed(dailyNumber: Int): Long {
        return dailyRandomSeedA * dailyNumber + dailyRandomSeedB
    }

    private fun randomSeedToDailyNumber(randomSeed: Long): Int {
        return ((randomSeed - dailyRandomSeedB) / dailyRandomSeedA).toInt()
    }

    private fun assertSeedVersionInteger(seedVersion: Int, encoded: Long) {
        assertSeedVersionInteger(seedVersion, encoded.toFakeB58())
    }

    private fun assertSeedVersionInteger(seedVersion: Int, encoded: String) {
        assertEquals(seedVersion, factory.getSeedVersionInteger(encoded))
    }

    private fun assertSeedVersion(seedVersion: SeedVersion, encoded: Long) {
        assertSeedVersion(seedVersion, encoded.toFakeB58())
    }

    private fun assertSeedVersion(seedVersion: SeedVersion, encoded: String) {
        assertEquals(seedVersion, factory.getSeedVersion(encoded))
    }

    private fun assertSeedVersionThrows(encoded: Long) {
        assertSeedVersionThrows(encoded.toFakeB58())
    }

    private fun assertSeedVersionThrows(encoded: String) {
        assertThrows(IllegalArgumentException::class.java) { factory.getSeedVersion(encoded) }
    }

    private fun createGameSetup(daily: Boolean, randomSeed: Long, version: Int) = GameSetup(
        GameSetup.Board(6),
        GameSetup.Evaluation(ConstraintPolicy.POSITIVE),
        GameSetup.Vocabulary(CodeLanguage.ENGLISH, GameSetup.Vocabulary.VocabularyType.LIST, 6, 26),
        GameSetup.Solver.PLAYER,
        GameSetup.Evaluator.HONEST,
        randomSeed = randomSeed,
        daily = daily,
        version = version
    )

    private fun createGameSetupSeedCore(daily: Boolean, randomSeed: Long, version: Int): String {
        val gameSetup = createGameSetup(daily, randomSeed, version)
        return factory.getSeedCore(gameSetup)
    }

    private fun calendarPlusMillis(millis: Long): Calendar {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = firstDaily.time.time + millis
        return calendar
    }

    private fun calendarPlusDays(days: Double) = calendarPlusMillis((days * (1000 * 60 * 60 * 24)).toLong())

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory: isDaily
    //---------------------------------------------------------------------------------------------

    @Test
    fun factory_isDaily() {
        assertTrue(factory.isDaily("#1"))
        assertTrue(factory.isDaily("#2"))
        assertTrue(factory.isDaily("#3"))
        assertTrue(factory.isDaily("#10"))
        assertTrue(factory.isDaily("#100"))
        assertTrue(factory.isDaily("#582"))
        assertTrue(factory.isDaily("#91830"))

        assertFalse(factory.isDaily())
        assertFalse(factory.isDaily(""))

        assertFalse(factory.isDaily("1"))
        assertFalse(factory.isDaily("2"))
        assertFalse(factory.isDaily("3"))
        assertFalse(factory.isDaily("10"))
        assertFalse(factory.isDaily("100"))
        assertFalse(factory.isDaily("582"))
        assertFalse(factory.isDaily("91830"))

        assertFalse(factory.isDaily("9o3"))
        assertFalse(factory.isDaily("XobN"))
        assertFalse(factory.isDaily("bExJ7"))
        assertFalse(factory.isDaily("Q7WTu33"))
        assertFalse(factory.isDaily("Jhiqzgs"))
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory: getSeedVersionInteger
    //---------------------------------------------------------------------------------------------

    @Test
    fun factory_getSeedVersionInteger_default() {
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger())
    }

    @Test
    fun factory_getSeedVersionInteger_daily() {
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#1"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#12"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#156"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#8766"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#500"))
    }

    @Test
    fun factory_getSeedVersionInteger_encoded_zeros() {
        assertSeedVersionInteger(0, 0)
        assertSeedVersionInteger(0, 10)
        assertSeedVersionInteger(0, 80)
        assertSeedVersionInteger(0, 9010)
        assertSeedVersionInteger(0, 76200)
        assertSeedVersionInteger(0, 777770)
        assertSeedVersionInteger(0, 100000)
    }

    @Test
    fun factory_getSeedVersionInteger_encoded_ones() {
        assertSeedVersionInteger(1, 1)
        assertSeedVersionInteger(1, 101)
        assertSeedVersionInteger(1, 801)
        assertSeedVersionInteger(1, 90101)
        assertSeedVersionInteger(1, 762001)
        assertSeedVersionInteger(1, 7777701)
        assertSeedVersionInteger(1, 1000001)
    }

    @Test
    fun factory_getSeedVersionInteger_encoded_numbers() {
        assertSeedVersionInteger(12, 8012)
        assertSeedVersionInteger(123, 9010123)
        assertSeedVersionInteger(178, 76200178)
        assertSeedVersionInteger(93276, 77777093276)
        assertSeedVersionInteger(9542878, 1000009542878)
    }

    @Test
    fun factory_getSeedVersionInteger_encoded_fakeB58Strings() {
        assertSeedVersionInteger(12, "9o3")
        assertSeedVersionInteger(123, "XobN")
        assertSeedVersionInteger(178, "bExJ7")
        assertSeedVersionInteger(93276, "Q7WTu33")
        assertSeedVersionInteger(9542878, "Jhiqzgs")
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory: getSeedVersion
    //---------------------------------------------------------------------------------------------

    @Test
    fun factory_getSeedVersion_default() {
        assertEquals(defaultVersion, factory.getSeedVersion())
    }

    @Test
    fun factory_getSeedVersion_daily() {
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#1"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#12"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#156"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#8766"))
        assertEquals(defaultVersion.numberEncoding, factory.getSeedVersionInteger("#500"))
    }

    @Test
    fun factory_getSeedVersion_encoded_zeros() {
        assertSeedVersionThrows(0)
        assertSeedVersionThrows(80)
        assertSeedVersionThrows(9010)
        assertSeedVersionThrows(76200)
        assertSeedVersionThrows(777770)
        assertSeedVersionThrows(100000)
    }

    @Test
    fun factory_getSeedVersion_encoded_ones() {
        assertSeedVersion(SeedVersion.V1, 1)
        assertSeedVersion(SeedVersion.V1, 101)
        assertSeedVersion(SeedVersion.V1, 801)
        assertSeedVersion(SeedVersion.V1, 90101)
        assertSeedVersion(SeedVersion.V1, 762001)
        assertSeedVersion(SeedVersion.V1, 7777701)
        assertSeedVersion(SeedVersion.V1, 1000001)
    }

    @Test
    fun factory_getSeedVersion_encoded_numbers() {
        assertSeedVersionThrows(8012)
        assertSeedVersionThrows(9010123)
        assertSeedVersionThrows(76200178)
        assertSeedVersionThrows(77777093276)
        assertSeedVersionThrows(1000009542878)
    }

    @Test
    fun factory_getSeedVersion_encoded_fakeB58Strings() {
        assertSeedVersionThrows("9o3")
        assertSeedVersionThrows("XobN")
        assertSeedVersionThrows("bExJ7")
        assertSeedVersionThrows("Q7WTu33")
        assertSeedVersionThrows("Jhiqzgs")
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory: getRandomSeed
    //---------------------------------------------------------------------------------------------

    @Test
    fun factory_getRandomSeed_encoded() {
        assertEquals(8, factory.getRandomSeed("9o3"))
        assertEquals(901, factory.getRandomSeed("XobN"))
        assertEquals(7620, factory.getRandomSeed("bExJ7"))
        assertEquals(77777, factory.getRandomSeed("Q7WTu33"))
        assertEquals(10000, factory.getRandomSeed("Jhiqzgs"))
    }

    @Test
    fun factory_getRandomSeed_daily() {
        assertEquals(dailyNumberToRandomSeed(1), factory.getRandomSeed("#1"))
        assertEquals(dailyNumberToRandomSeed(19771), factory.getRandomSeed("#19771"))
        assertEquals(dailyNumberToRandomSeed(1256), factory.getRandomSeed("#1256"))
        assertEquals(dailyNumberToRandomSeed(55), factory.getRandomSeed("#55"))
        assertEquals(dailyNumberToRandomSeed(876), factory.getRandomSeed("#876"))
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region SeedCoreFactory: getSeedCore
    //---------------------------------------------------------------------------------------------

    @Test
    fun factory_getSeedCore_daily() {
        assertEquals("#1", createGameSetupSeedCore(true, dailyNumberToRandomSeed(1), 1))
        assertEquals("#1", createGameSetupSeedCore(true, dailyNumberToRandomSeed(1), 0))
        assertEquals("#1", createGameSetupSeedCore(true, dailyNumberToRandomSeed(1), 7077))

        assertEquals("#139", createGameSetupSeedCore(true, dailyNumberToRandomSeed(139), 1))
        assertEquals("#139", createGameSetupSeedCore(true, dailyNumberToRandomSeed(139), 0))
        assertEquals("#139", createGameSetupSeedCore(true, dailyNumberToRandomSeed(139), 7077))

        assertEquals("#7177", createGameSetupSeedCore(true, dailyNumberToRandomSeed(7177), 1))
        assertEquals("#7177", createGameSetupSeedCore(true, dailyNumberToRandomSeed(7177), 0))
        assertEquals("#7177", createGameSetupSeedCore(true, dailyNumberToRandomSeed(7177), 7077))
    }

    @Test
    fun factory_getSeedCore_seeded() {
        assertEquals("9o3", createGameSetupSeedCore(false, 8, 12))
        assertEquals("XobN", createGameSetupSeedCore(false, 901, 123))
        assertEquals("bExJ7", createGameSetupSeedCore(false, 7620, 178))
        assertEquals("Q7WTu33", createGameSetupSeedCore(false, 77777, 93276))
        assertEquals("Jhiqzgs", createGameSetupSeedCore(false, 10000, 9542878))
    }

    @Test
    fun factory_getDailySeedCore() {
        // first day: #1
        assertEquals("#1", factory.getDailySeedCore(firstDaily))

        // still the first day
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.0)))
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.01)))
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.1)))
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.5)))
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.9)))
        assertEquals("#1", factory.getDailySeedCore(calendarPlusDays(0.99999)))

        // day 2
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.0)))
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.01)))
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.1)))
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.5)))
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.9)))
        assertEquals("#2", factory.getDailySeedCore(calendarPlusDays(1.99999)))

        // day 402
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.0)))
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.01)))
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.1)))
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.5)))
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.9)))
        assertEquals("#402", factory.getDailySeedCore(calendarPlusDays(401.99999)))

        // day 403
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.0)))
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.01)))
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.1)))
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.5)))
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.9)))
        assertEquals("#403", factory.getDailySeedCore(calendarPlusDays(402.99999)))
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //---------------------------------------------------------------------------------------------
    //endregion

}