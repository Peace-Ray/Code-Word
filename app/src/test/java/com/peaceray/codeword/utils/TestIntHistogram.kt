package com.peaceray.codeword.utils

import com.peaceray.codeword.utils.histogram.IntHistogram
import org.junit.Test

import org.junit.Assert.*

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
class TestIntHistogram {

    //region Constants
    //---------------------------------------------------------------------------------------------
    private val emptyMap = mapOf<Int, Int>()
    private val populatedMap = mapOf(Pair(1, 2), Pair(2, 0), Pair(3, 7), Pair(6, 10), Pair(7, -2), Pair(8, 10), Pair(10, 1))
    private val populatedMapAsString = ">1=2,>2=0,>3=7,>6=10,>7=-2,>8=10,>10=1"
    private val widespreadKeys: Set<Int> = setOf(-10000000, -1000, -999, -500, -100, -50, -10, -5, -2, -1, 0, 1, 2, 3, 4, 5, 10, 50, 100, 1000, 100000000)
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Construction
    //---------------------------------------------------------------------------------------------
    @Test
    fun construction_empty() {
        IntHistogram().assertEquals()
        IntHistogram(min = 0).assertEquals(min = 0)
        IntHistogram(max = 0).assertEquals(max = 0)

        IntHistogram(min = -5, max = 20).assertEquals(min = -5, max = 20)
        IntHistogram(min = -5, max = 20, default = 3).assertEquals(min = -5, max = 20, default = 3)
    }

    @Test
    fun construction_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(default = 1)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(default = 1, min = -5)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(default = 1, max = 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 2, max = 1)
        }
    }

    @Test
    fun construction_fromString_empty() {
        IntHistogram.fromString("").assertEquals()
        IntHistogram.fromString(">min=0").assertEquals(min = 0)
        IntHistogram.fromString(">max=0").assertEquals(max = 0)

        IntHistogram.fromString(">min=-5,>max=20").assertEquals(min = -5, max = 20)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").assertEquals(min = -5, max = 20, default = 3)
    }

    @Test
    fun construction_fromString_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">default=1")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">default=1,min=-5")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">default=1,max=10")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=2,max=1")
        }
    }

    @Test
    fun construction_fromString_populated() {
        IntHistogram.fromString(populatedMapAsString).assertEquals(populatedMap)
        IntHistogram.fromString("$populatedMapAsString,>min=0").assertEquals(populatedMap, min = 0)
        IntHistogram.fromString(">max=10,$populatedMapAsString").assertEquals(populatedMap, max = 10)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").assertEquals(populatedMap, min = -5, max = 20)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").assertEquals(populatedMap, min = -5, max = 20, default = 3)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: String Representation
    //---------------------------------------------------------------------------------------------
    @Test
    fun stringRepresentation_empty() {
        assertEquals(">default=0", IntHistogram().toString())
        assertEquals(">default=0", IntHistogram(default = 0).toString())
        assertEquals(">default=0,>min=1", IntHistogram(min = 1).toString())
        assertEquals(">default=0,>max=10", IntHistogram(max = 10).toString())
        assertEquals(">default=0,>min=1,>max=9", IntHistogram(min = 1, max = 9).toString())
        assertEquals(">default=3,>min=1,>max=9", IntHistogram(min = 1, max = 9, default = 3).toString())
    }

    @Test
    fun stringRepresentation_populated() {
        IntHistogram().insertAllTogether(populatedMap).toString()
            .assertEquals(populatedMap)

        IntHistogram(min = 1).insertAllTogether(populatedMap).toString()
            .assertEquals(populatedMap, min = 1)

        IntHistogram(max = 10).insertAllTogether(populatedMap).toString()
            .assertEquals(populatedMap, max = 10)

        IntHistogram(min = 1, max = 10, default = 3).insertAllTogether(populatedMap).toString()
            .assertEquals(populatedMap, min = 1, max = 10, default = 3)
    }

    @Test
    fun stringRepresentation_afterOperations() {
        val histogram = IntHistogram()
        val entries = mutableMapOf<Int, Int>()

        histogram[1] = 2
        entries[1] = 2
        histogram.toString().assertEquals(entries)

        histogram[2] = 5
        entries[2] = 5
        histogram[4] = 7
        entries[4] = 7
        histogram.toString().assertEquals(entries)

        histogram.remove(2)
        entries.remove(2)
        histogram.toString().assertEquals(entries)

        histogram.setRange(0, 10)
        histogram.toString().assertEquals(entries, min = 0, max = 10)

        histogram.reset(min = -2, max = 12, default = 3, from = entries)
        histogram.toString().assertEquals(entries, min = -2, max = 12, default = 3)

        histogram[5] = 1
        entries[5] = 1
        histogram[6] = 3
        entries[6] = 3
        histogram.toString().assertEquals(entries, min = -2, max = 12, default = 3)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Set Range
    //---------------------------------------------------------------------------------------------
    @Test
    fun setRange_empty() {
        IntHistogram(min = -5, max = 20).let{
            it.setRange() ; it
        }.assertEquals()

        IntHistogram(min = -5, max = 20).let{
            it.setRange(min = 0) ; it
        }.assertEquals(min = 0)

        IntHistogram(min = -5, max = 20).let{
            it.setRange(max = 0) ; it
        }.assertEquals(max = 0)

        IntHistogram(min = -5, max = 20).let{
            it.setRange(min = 2, max = 10) ; it
        }.assertEquals(min = 2, max = 10)
    }

    @Test
    fun setRange_invalid() {
        // State: can't setRange with nonzero default
        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).setRange()
        }

        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).setRange(min = 0)
        }

        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).setRange(max = 0)
        }

        // Argument: can't set min > max
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = -5, max = 20).setRange(max = 1, min = 2)
        }
    }

    @Test
    fun setRange_populated() {
        IntHistogram(min = -5, max = 20).let{
            it.insertAllIndividually(populatedMap)
            it.setRange() ; it
        }.assertEquals(entries = populatedMap)

        IntHistogram(min = -5, max = 20).let{
            it.insertAllIndividually(populatedMap)
            it.setRange(min = 0) ; it
        }.assertEquals(entries = populatedMap, min = 0)

        IntHistogram(min = -5, max = 20).let{
            it.insertAllIndividually(populatedMap)
            it.setRange(max = 10) ; it
        }.assertEquals(entries = populatedMap, max = 10)

        IntHistogram(min = -5, max = 20).let{
            it.insertAllIndividually(populatedMap)
            it.setRange(min = 0, max = 10) ; it
        }.assertEquals(entries = populatedMap, min = 0, max = 10)
    }

    @Test
    fun setRange_populated_invalid() {
        // State: can't setRange with nonzero default
        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).insertAllIndividually(populatedMap).setRange()
        }

        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).insertAllIndividually(populatedMap).setRange(min = 0)
        }

        assertThrows(IllegalStateException::class.java) {
            IntHistogram(min = -5, max = 20, default = 3).insertAllIndividually(populatedMap).setRange(max = 20)
        }

        // Argument: can't set min > max
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = -5, max = 20).insertAllIndividually(populatedMap).setRange(min = 2, max = 1)
        }

        // Argument: can't set range to exclude extant values
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = -5, max = 20).insertAllIndividually(populatedMap).setRange(min = 2)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = -5, max = 20).insertAllIndividually(populatedMap).setRange(max = 9)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Reset
    //---------------------------------------------------------------------------------------------
    @Test
    fun reset_fromEmpty_toEmpty() {
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(from = emptyMap).assertEquals()
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = 0, from = emptyMap).assertEquals(min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(max = 0, from = emptyMap).assertEquals(max = 0)

        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = -2, max = 10, from = emptyMap).assertEquals(min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = -1, max = 5, default = 10, from = emptyMap).assertEquals(min = -1, max = 5, default = 10)
    }

    @Test
    fun reset_fromEmpty_toPopulated() {
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(from = populatedMap).assertEquals(populatedMap)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = 0, from = populatedMap).assertEquals(populatedMap, min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(max = 10, from = populatedMap).assertEquals(populatedMap, max = 10)

        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = -2, max = 10, from = populatedMap).assertEquals(populatedMap, min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = -1, max = 20, default = 10, from = populatedMap).assertEquals(populatedMap, min = -1, max = 20, default = 10)
    }

    @Test
    fun reset_fromEmpty_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(default = 10, from = emptyMap)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(default = 10, from = populatedMap)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").reset(min = 2, max = 1, from = emptyMap)
        }
    }

    @Test
    fun reset_fromPopulated_toEmpty() {
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(from = emptyMap).assertEquals()
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = 0, from = emptyMap).assertEquals(min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(max = 0, from = emptyMap).assertEquals(max = 0)

        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = -2, max = 10, from = emptyMap).assertEquals(min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = -1, max = 5, default = 10, from = emptyMap).assertEquals(min = -1, max = 5, default = 10)
    }

    @Test
    fun reset_fromPopulated_toPopulated() {
        val repopulatedMap = mapOf(Pair(1, 5), Pair(2, 2), Pair(3, 4))

        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(from = repopulatedMap).assertEquals(repopulatedMap)
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = 0, from = repopulatedMap).assertEquals(repopulatedMap, min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(max = 5, from = repopulatedMap).assertEquals(repopulatedMap, max = 5)

        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = -2, max = 10, from = repopulatedMap).assertEquals(repopulatedMap, min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = -1, max = 5, default = 10, from = repopulatedMap).assertEquals(repopulatedMap, min = -1, max = 5, default = 10)
    }

    @Test
    fun reset_fromPopulated_invalid() {
        val repopulatedMap = mapOf(Pair(1, 5), Pair(2, 2), Pair(3, 4))

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(default = 10, from = emptyMap)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(default = 10, from = repopulatedMap)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").reset(min = 2, max = 1, from = emptyMap)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Reset From String
    //---------------------------------------------------------------------------------------------
    @Test
    fun resetFromString_fromEmpty_toEmpty() {
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString("").assertEquals()
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=0").assertEquals(min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">max=0").assertEquals(max = 0)

        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=-2,>max=10").assertEquals(min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=-1,>max=5,>default=10").assertEquals(min = -1, max = 5, default = 10)
    }

    @Test
    fun resetFromString_fromEmpty_toPopulated() {
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(populatedMapAsString).assertEquals(populatedMap)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=0,$populatedMapAsString").assertEquals(populatedMap, min = 0)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString("$populatedMapAsString,>max=10").assertEquals(populatedMap, max = 10)

        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=-2,>max=10,$populatedMapAsString").assertEquals(populatedMap, min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">min=-1,>max=15,$populatedMapAsString,>default=10").assertEquals(populatedMap, min = -1, max = 15, default = 10)
    }

    @Test
    fun resetFromString_fromEmpty_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString(">default=10")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString("$populatedMapAsString,>default=10")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3").resetFromString("$populatedMapAsString,>min=2,>max=1")
        }
    }

    @Test
    fun resetFromString_fromPopulated_toEmpty() {
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString("").assertEquals()
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=0").assertEquals(min = 0)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">max=0").assertEquals(max = 0)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=-2,>max=10").assertEquals(min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=-1,>max=5,>default=10").assertEquals(min = -1, max = 5, default = 10)
    }

    @Test
    fun resetFromString_fromPopulated_toPopulated() {
        val repopulatedMap = mapOf(Pair(1, 5), Pair(2, 2), Pair(3, 4))
        val repopulatedMapAsString = ">1=5,>2=2,>3=4"

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(repopulatedMapAsString).assertEquals(repopulatedMap)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=0,$repopulatedMapAsString").assertEquals(repopulatedMap, min = 0)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString("$repopulatedMapAsString,>max=10").assertEquals(repopulatedMap, max = 10)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=-2,>max=10,$repopulatedMapAsString").assertEquals(repopulatedMap, min = -2, max = 10)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").resetFromString(">min=-1,>max=15,$repopulatedMapAsString,>default=10").assertEquals(repopulatedMap, min = -1, max = 15, default = 10)
    }

    @Test
    fun resetFromString_fromPopulated_invalid() {
        val repopulatedMapAsString = ">1=5,>2=2,>3=4"

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").resetFromString(">default=10")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").resetFromString("$repopulatedMapAsString,>default=10")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").resetFromString("$repopulatedMapAsString,>min=2")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").resetFromString("$repopulatedMapAsString,>max=2")
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.fromString(">min=-5,>max=20,>default=3,$populatedMapAsString").resetFromString("$repopulatedMapAsString,>min=2,>max=1")
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Sum
    //---------------------------------------------------------------------------------------------
    @Test
    fun sum_defaultZero() {
        assertEquals(0, IntHistogram().sum())
        assertEquals(0, IntHistogram(default = 0).sum())
        assertEquals(0, IntHistogram(min = 0, max = 50, default = 0).sum())

        val tinyMap = mapOf(Pair(1, 3), Pair(5, 2))
        val tinySum = 5
        assertEquals(tinySum, IntHistogram().insertAllIndividually(tinyMap).sum())
        assertEquals(tinySum, IntHistogram(default = 0).insertAllIndividually(tinyMap).sum())
        assertEquals(tinySum, IntHistogram(min = 0, max = 50, default = 0).insertAllIndividually(tinyMap).sum())

        val sum = populatedMap.values.sum()
        assertEquals(sum, IntHistogram().insertAllIndividually(populatedMap).sum())
        assertEquals(sum, IntHistogram(default = 0).insertAllIndividually(populatedMap).sum())
        assertEquals(sum, IntHistogram(min = 0, max = 50, default = 0).insertAllIndividually(populatedMap).sum())
    }

    @Test
    fun sum_defaultNonZero() {
        assertEquals(102, IntHistogram(min = 0, max = 50, default = 2).sum())

        assertEquals(5, IntHistogram(min = 1, max = 2, default = 100).insertAllIndividually(mapOf(Pair(1, 4), Pair(2, 1))).sum())

        val tinyMap = mapOf(Pair(1, 4), Pair(5, 1))
        val tinySum = 5
        val unusedSpread = 3
        assertEquals(tinySum + unusedSpread * 2, IntHistogram(min = 1, max = 5, default = 2).insertAllIndividually(tinyMap).sum())
        assertEquals(tinySum + (unusedSpread + 2) * 2, IntHistogram(min = 0, max = 6, default = 2).insertAllIndividually(tinyMap).sum())
        assertEquals(tinySum + (unusedSpread + 6) * 2, IntHistogram(min = 0, max = 10, default = 2).insertAllIndividually(tinyMap).sum())

        val sum = populatedMap.values.sum()
        assertEquals(sum + (51 - populatedMap.size) * 2, IntHistogram(min = 0, max = 50, default = 2).insertAllIndividually(populatedMap).sum())
        assertEquals(sum + (16 - populatedMap.size) * 3, IntHistogram(min = 0, max = 15, default = 3).insertAllIndividually(populatedMap).sum())
        assertEquals(sum + (16 - populatedMap.size) * 7, IntHistogram(min = -5, max = 10, default = 7).insertAllIndividually(populatedMap).sum())
        assertEquals(sum + (11 - populatedMap.size) * 7, IntHistogram(min = 0, max = 10, default = 7).insertAllIndividually(populatedMap).sum())
    }

    @Test
    fun sum_predicate_defaultZero() {
        assertEquals(0, IntHistogram().sum { it.key % 2 == 0 } )
        assertEquals(0, IntHistogram(default = 0).sum { it.key % 2 == 0 })
        assertEquals(0, IntHistogram(min = 0, max = 50, default = 0).sum { it.key % 2 == 0 })

        val tinyMap = mapOf(Pair(1, 3), Pair(5, 2))
        assertEquals(0, IntHistogram().insertAllIndividually(tinyMap).sum { it.key % 2 == 0 })
        assertEquals(5, IntHistogram().insertAllIndividually(tinyMap).sum { it.key % 2 == 1 })
        assertEquals(3, IntHistogram().insertAllIndividually(tinyMap).sum { it.key == 1 })
        assertEquals(5, IntHistogram(min = 0, max = 50, default = 0).insertAllIndividually(tinyMap).sum { it.key % 2 == 1 })

        val evenKeySum = populatedMap.filter { it.key % 2 == 0 }.values.sum()
        assertEquals(evenKeySum, IntHistogram().insertAllIndividually(populatedMap).sum { it.key % 2 == 0 })
        assertEquals(evenKeySum, IntHistogram(default = 0).insertAllIndividually(populatedMap).sum { it.key % 2 == 0 })
        assertEquals(evenKeySum, IntHistogram(min = 0, max = 50, default = 0).insertAllIndividually(populatedMap).sum { it.key % 2 == 0 })
    }

    @Test
    fun sum_predicate_defaultNonZero() {
        assertEquals(52, IntHistogram(min = 0, max = 50, default = 2).sum { it.key % 2 == 0 } )
        assertEquals(50, IntHistogram(min = 0, max = 50, default = 2).sum { it.key % 2 == 1 } )

        val miniMap = mapOf(Pair(1, 4), Pair(2, 1))
        assertEquals(4, IntHistogram(min = 1, max = 2, default = 2).insertAllIndividually(miniMap).sum { it.key % 2 == 1 } )
        assertEquals(1, IntHistogram(min = 1, max = 2, default = 2).insertAllIndividually(miniMap).sum { it.key % 2 == 0 } )
        assertEquals(3, IntHistogram(min = 0, max = 3, default = 2).insertAllIndividually(miniMap).sum { it.key % 2 == 0 } )

        val tinyMap = mapOf(Pair(1, 4), Pair(5, 1))
        val tinySum = 5
        assertEquals(tinySum + 2, IntHistogram(min = 1, max = 5, default = 2).insertAllIndividually(tinyMap).sum { it.key % 2 == 1 } )
        assertEquals(4, IntHistogram(min = 1, max = 5, default = 2).insertAllIndividually(tinyMap).sum { it.key % 2 == 0 } )
        assertEquals(tinySum + 2, IntHistogram(min = 0, max = 6, default = 2).insertAllIndividually(tinyMap).sum { it.key % 2 == 1 } )
        assertEquals(tinySum + 6, IntHistogram(min = 0, max = 10, default = 2).insertAllIndividually(tinyMap).sum { it.key % 2 == 1 } )

        val evenPopulatedMap = populatedMap.filter { it.key % 2 == 0 }
        val evenKeySum = evenPopulatedMap.values.sum()
        val evenKeys = evenPopulatedMap.keys
        assertEquals(
            evenKeySum + (0..10 step 2).filter { it !in evenKeys }.size * 2,
            IntHistogram(min = 0, max = 10, default = 2).insertAllIndividually(populatedMap).sum { it.key % 2 == 0 }

        )
        assertEquals(
            evenKeySum + (0..50 step 2).filter { it !in evenKeys }.size * 3,
            IntHistogram(min = 0, max = 50, default = 3).insertAllIndividually(populatedMap).sum { it.key % 2 == 0 }
        )

        assertEquals(
            evenKeySum + (-4..14 step 2).filter { it !in evenKeys }.size * 3,
            IntHistogram(min = -5, max = 15, default = 3).insertAllIndividually(populatedMap).sum { it.key % 2 == 0 }
        )
    }

    @Test
    fun sum_indexPredicate_defaultZero() {
        val histogram = IntHistogram().insertAllIndividually(populatedMap)

        assertEquals(0, histogram.sum(setOf()) { true } )
        assertEquals(0, histogram.sum(setOf(1)) { it.key == 0 } )

        assertEquals(histogram[1], histogram.sum(setOf(1)) { true } )
        assertEquals(histogram[1], histogram.sum(setOf(1)) { it.key == 1 } )

        assertEquals(histogram[1] + histogram[3], histogram.sum(setOf(1, 2, 3, 4)) { it.key % 2 == 1 } )

    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Insertion
    //---------------------------------------------------------------------------------------------
    @Test
    fun put_startEmpty() {
        val histogram = IntHistogram()
        histogram[1] = 2
        histogram[4] = 5
        histogram[5] = 0
        histogram.assertEquals(mapOf(Pair(1, 2), Pair(4, 5), Pair(5, 0)))

        IntHistogram().insertAllIndividually(populatedMap).assertEquals(populatedMap)
        IntHistogram(min = 0).insertAllIndividually(populatedMap).assertEquals(populatedMap, min = 0)
        IntHistogram(max = 10).insertAllIndividually(populatedMap).assertEquals(populatedMap, max = 10)

        IntHistogram(min = -5, max = 20).insertAllIndividually(populatedMap).assertEquals(populatedMap, min = -5, max = 20)
        IntHistogram(min = -5, max = 20, default = 3).insertAllIndividually(populatedMap).assertEquals(populatedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun put_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 2)[1] = 10
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(max = 5)[6] = 10
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 0, max = 10)[-1] = 1
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 0, max = 10)[11] = 1
        }
    }

    @Test
    fun put_startFromString() {
        val tempMap = populatedMap.toMutableMap()
        val modifications = listOf(Pair(2, 7), Pair(-1, 2), Pair(5, 3), Pair(11, 0))
        tempMap.putAll(modifications)
        val modifiedMap = tempMap.toMap()

        IntHistogram.fromString(populatedMapAsString).insertAllIndividually(modifications).assertEquals(modifiedMap)
        IntHistogram.fromString("$populatedMapAsString,>min=-1").insertAllIndividually(modifications).assertEquals(modifiedMap, min = -1)
        IntHistogram.fromString(">max=12,$populatedMapAsString").insertAllIndividually(modifications).assertEquals(modifiedMap, max = 12)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").insertAllIndividually(modifications).assertEquals(modifiedMap, min = -5, max = 20)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").insertAllIndividually(modifications).assertEquals(modifiedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun putAll_startEmpty() {
        val histogram = IntHistogram()
        histogram[1] = 2
        histogram[4] = 5
        histogram[5] = 0
        histogram.assertEquals(mapOf(Pair(1, 2), Pair(4, 5), Pair(5, 0)))

        IntHistogram().insertAllTogether(populatedMap).assertEquals(populatedMap)
        IntHistogram(min = 0).insertAllTogether(populatedMap).assertEquals(populatedMap, min = 0)
        IntHistogram(max = 10).insertAllTogether(populatedMap).assertEquals(populatedMap, max = 10)

        IntHistogram(min = -5, max = 20).insertAllTogether(populatedMap).assertEquals(populatedMap, min = -5, max = 20)
        IntHistogram(min = -5, max = 20, default = 3).insertAllTogether(populatedMap).assertEquals(populatedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun putAll_invalid() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 2)[1] = 10
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(max = 5)[6] = 10
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 0, max = 10)[-1] = 1
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram(min = 0, max = 10)[11] = 1
        }
    }

    @Test
    fun putAll_startFromString() {
        val tempMap = populatedMap.toMutableMap()
        val modifications = listOf(Pair(2, 7), Pair(-1, 2), Pair(5, 3), Pair(11, 0))
        tempMap.putAll(modifications)
        val modifiedMap = tempMap.toMap()

        IntHistogram.fromString(populatedMapAsString).insertAllTogether(modifications).assertEquals(modifiedMap)
        IntHistogram.fromString("$populatedMapAsString,>min=-1").insertAllTogether(modifications).assertEquals(modifiedMap, min = -1)
        IntHistogram.fromString(">max=12,$populatedMapAsString").insertAllTogether(modifications).assertEquals(modifiedMap, max = 12)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").insertAllTogether(modifications).assertEquals(modifiedMap, min = -5, max = 20)
        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").insertAllTogether(modifications).assertEquals(modifiedMap, min = -5, max = 20, default = 3)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Instance Tests: Removal
    //---------------------------------------------------------------------------------------------
    @Test
    fun remove_singleElement() {
        val tempMap = populatedMap.toMutableMap()
        tempMap.remove(1)
        val modifiedMap = tempMap.toMap()

        IntHistogram.fromString(populatedMapAsString).let {
            assertEquals(populatedMap[1], it.remove(1)) ; it
        }.assertEquals(modifiedMap)

        IntHistogram.fromString("$populatedMapAsString,>min=0").let {
            assertEquals(populatedMap[1], it.remove(1)) ; it
        }.assertEquals(modifiedMap, min = 0)

        IntHistogram.fromString(">max=10,$populatedMapAsString").let {
            assertEquals(populatedMap[1], it.remove(1)) ; it
        }.assertEquals(modifiedMap, max = 10)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").let {
            assertEquals(populatedMap[1], it.remove(1)) ; it
        }.assertEquals(modifiedMap, min = -5, max = 20)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").let {
            assertEquals(populatedMap[1], it.remove(1)) ; it
        }.assertEquals(modifiedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun remove_multipleElements() {
        val tempMap = populatedMap.toMutableMap()
        tempMap.remove(1)
        tempMap.remove(2)
        tempMap.remove(3)
        val modifiedMap = tempMap.toMap()

        IntHistogram.fromString(populatedMapAsString).let {
            assertEquals(populatedMap[1], it.remove(1))
            assertEquals(populatedMap[2], it.remove(2))
            assertEquals(populatedMap[3], it.remove(3))
            assertEquals(0, it.remove(3))
            it
        }.assertEquals(modifiedMap)

        IntHistogram.fromString("$populatedMapAsString,>min=0").let {
            assertEquals(populatedMap[1], it.remove(1))
            assertEquals(populatedMap[2], it.remove(2))
            assertEquals(populatedMap[3], it.remove(3))
            assertEquals(0, it.remove(3))
            it
        }.assertEquals(modifiedMap, min = 0)

        IntHistogram.fromString(">max=10,$populatedMapAsString").let {
            assertEquals(populatedMap[1], it.remove(1))
            assertEquals(populatedMap[2], it.remove(2))
            assertEquals(populatedMap[3], it.remove(3))
            assertEquals(0, it.remove(3))
            it
        }.assertEquals(modifiedMap, max = 10)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").let {
            assertEquals(populatedMap[1], it.remove(1))
            assertEquals(populatedMap[2], it.remove(2))
            assertEquals(populatedMap[3], it.remove(3))
            assertEquals(0, it.remove(3))
            it
        }.assertEquals(modifiedMap, min = -5, max = 20)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").let {
            assertEquals(populatedMap[1], it.remove(1))
            assertEquals(populatedMap[2], it.remove(2))
            assertEquals(populatedMap[3], it.remove(3))
            assertEquals(3, it.remove(3))
            it
        }.assertEquals(modifiedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun remove_expectedReturnValue() {
        IntHistogram.fromString(populatedMapAsString).let {
            // 1 -> 2
            assertEquals(2, it.remove(1))
            assertEquals(0, it.remove(1))

            // 2 -> 0
            assertEquals(0, it.remove(2))
            assertEquals(0, it.remove(2))

            // 3 -> 7
            assertEquals(7, it.remove(3))
            assertEquals(0, it.remove(3))
        }

        IntHistogram.fromString(">min=0,>max=100,>default=7,$populatedMapAsString").let {
            // 1 -> 2
            assertFalse(it.remove(1, 1))
            assertTrue(it.remove(1, 2))
            assertFalse(it.remove(1, 2))
            assertFalse(it.remove(1, 0))
            assertTrue(it.remove(1, 7))

            // 2 -> 0
            assertFalse(it.remove(2, 1))
            assertTrue(it.remove(2, 0))
            assertFalse(it.remove(2, 1))
            assertFalse(it.remove(2, 0))
            assertTrue(it.remove(2, 7))

            // 3 -> 7
            assertFalse(it.remove(3, 1))
            assertTrue(it.remove(3, 7))
            assertFalse(it.remove(3, 0))
            assertTrue(it.remove(3, 7))
        }
    }

    @Test
    fun remove_specifiedValue_works() {
        val tempMap = populatedMap.toMutableMap()
        tempMap.remove(1)
        tempMap.remove(2)
        tempMap.remove(3)
        val modifiedMap = tempMap.toMap()

        IntHistogram.fromString(populatedMapAsString).let {
            assertEquals(true, it.remove(1, 2))
            assertEquals(true, it.remove(2, 0))
            assertEquals(true, it.remove(3, 7))
            assertEquals(false, it.remove(3, 7))
            it
        }.assertEquals(modifiedMap)

        IntHistogram.fromString("$populatedMapAsString,>min=0").let {
            assertEquals(true, it.remove(1, 2))
            assertEquals(true, it.remove(2, 0))
            assertEquals(true, it.remove(3, 7))
            assertEquals(false, it.remove(3, 7))
            it
        }.assertEquals(modifiedMap, min = 0)

        IntHistogram.fromString(">max=10,$populatedMapAsString").let {
            assertEquals(true, it.remove(1, 2))
            assertEquals(true, it.remove(2, 0))
            assertEquals(true, it.remove(3, 7))
            assertEquals(false, it.remove(3, 7))
            it
        }.assertEquals(modifiedMap, max = 10)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").let {
            assertEquals(true, it.remove(1, 2))
            assertEquals(true, it.remove(2, 0))
            assertEquals(true, it.remove(3, 7))
            assertEquals(false, it.remove(3, 7))
            assertEquals(0, it.remove(3))
            it
        }.assertEquals(modifiedMap, min = -5, max = 20)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").let {
            assertEquals(true, it.remove(1, 2))
            assertEquals(true, it.remove(2, 0))
            assertEquals(true, it.remove(3, 7))
            assertEquals(false, it.remove(3, 7))
            it
        }.assertEquals(modifiedMap, min = -5, max = 20, default = 3)
    }

    @Test
    fun remove_specifiedValue_expectedReturnValue() {
        IntHistogram.fromString(populatedMapAsString).let {
            // 1 -> 2
            assertFalse(it.remove(1, 1))
            assertFalse(it.remove(1, 0))
            assertTrue(it.remove(1, 2))
            assertFalse(it.remove(1, 2))
            assertTrue(it.remove(1, 0))

            // 2 -> 0
            assertFalse(it.remove(2, 1))
            assertTrue(it.remove(2, 0))
            assertFalse(it.remove(2, 1))
            assertTrue(it.remove(2, 0))

            // 3 -> 7
            assertFalse(it.remove(3, 1))
            assertFalse(it.remove(3, 0))
            assertTrue(it.remove(3, 7))
            assertFalse(it.remove(3, 7))
            assertTrue(it.remove(3, 0))
        }

        IntHistogram.fromString(">min=0,>max=100,>default=7,$populatedMapAsString").let {
            // 1 -> 2
            assertFalse(it.remove(1, 1))
            assertTrue(it.remove(1, 2))
            assertFalse(it.remove(1, 2))
            assertFalse(it.remove(1, 0))
            assertTrue(it.remove(1, 7))

            // 2 -> 0
            assertFalse(it.remove(2, 1))
            assertTrue(it.remove(2, 0))
            assertFalse(it.remove(2, 1))
            assertFalse(it.remove(2, 0))
            assertTrue(it.remove(2, 7))

            // 3 -> 7
            assertFalse(it.remove(3, 1))
            assertTrue(it.remove(3, 7))
            assertFalse(it.remove(3, 0))
            assertTrue(it.remove(3, 7))
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region String Format Tests: Insertion
    //---------------------------------------------------------------------------------------------
    @Test
    fun stringFormat_set_newValue() {
        assertEquals(">default=0,>1=10", IntHistogram.setInString(">default=0", 1, 10))
        assertEquals(">default=0,>1=5", IntHistogram.setInString(">default=0", 1, 5))
        assertEquals(">default=0", IntHistogram.setInString(">default=0", 1, 0))
        assertEquals(">default=0,>1=-3", IntHistogram.setInString(">default=0", 1, -3))

        assertEquals(">default=0,>1=2,>4=10,>6=8", IntHistogram.setInString(">default=0,>1=2,>4=10", 6, 8))
        assertEquals(">default=0,>1=2,>4=10", IntHistogram.setInString(">default=0,>1=2,>4=10", 6, 0))
        assertEquals(">default=0,>1=2,>4=10,>6=-2", IntHistogram.setInString(">default=0,>1=2,>4=10", 6, -2))

        assertEquals(">default=2,>min=0,>max=10,>1=0", IntHistogram.setInString(">default=2,>min=0,>max=10", 1, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=1", IntHistogram.setInString(">default=2,>min=0,>max=10", 1, 1))
        assertEquals(">default=2,>min=0,>max=10", IntHistogram.setInString(">default=2,>min=0,>max=10", 1, 2))
        assertEquals(">default=2,>min=0,>max=10,>1=-2", IntHistogram.setInString(">default=2,>min=0,>max=10", 1, -2))

        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=0", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=1", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, 1))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, 2))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=-2", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, -2))
    }

    @Test
    fun stringFormat_set_extantValue() {
        assertEquals(">default=0,>1=10", IntHistogram.setInString(">default=0,>1=1", 1, 10))
        assertEquals(">default=0,>1=1", IntHistogram.setInString(">default=0,>1=1", 1, 1))
        assertEquals(">default=0", IntHistogram.setInString(">default=0,>1=1", 1, 0))
        assertEquals(">default=0,>1=-1", IntHistogram.setInString(">default=0,>1=1", 1, -1))

        assertEquals(">default=0,>1=10,>2=4", IntHistogram.setInString(">default=0,>1=1,>2=4", 1, 10))
        assertEquals(">default=0,>1=1,>2=4", IntHistogram.setInString(">default=0,>1=1,>2=4", 1, 1))
        assertEquals(">default=0,>2=4", IntHistogram.setInString(">default=0,>1=1,>2=4", 1, 0))
        assertEquals(">default=0,>1=-1,>2=4", IntHistogram.setInString(">default=0,>1=1,>2=4", 1, -1))

        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=0", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=1", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, 1))
        assertEquals(">default=2,>min=0,>max=10,>1=5", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, 2))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=-2", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, -2))

        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=0,>3=4", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=1,>3=4", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, 1))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>3=4", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, 2))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=-2,>3=4", IntHistogram.setInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, -2))
    }

    @Test
    fun stringFormat_set_outOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.setInString(">default=0,>min=5", 1, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.setInString(">default=0,>max=5", 6, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.setInString(">default=0,>min=1,>max=5", 6, 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.setInString(">default=0,>min=1,>max=5", -1, 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.setInString(">default=0,>min=1,>max=5", 6, 10)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region String Format Tests: Addition
    //---------------------------------------------------------------------------------------------
    @Test
    fun stringFormat_add_newValue() {
        assertEquals(">default=0,>1=10", IntHistogram.addInString(">default=0", 1, 10))
        assertEquals(">default=0,>1=5", IntHistogram.addInString(">default=0", 1, 5))
        assertEquals(">default=0", IntHistogram.addInString(">default=0", 1, 0))

        assertEquals(">default=0,>1=2,>4=10,>6=8", IntHistogram.addInString(">default=0,>1=2,>4=10", 6, 8))
        assertEquals(">default=0,>1=2,>4=10", IntHistogram.addInString(">default=0,>1=2,>4=10", 6, 0))

        assertEquals(">default=2,>min=0,>max=10,>1=0", IntHistogram.addInString(">default=2,>min=0,>max=10", 1, -2))
        assertEquals(">default=2,>min=0,>max=10,>1=1", IntHistogram.addInString(">default=2,>min=0,>max=10", 1, -1))
        assertEquals(">default=2,>min=0,>max=10", IntHistogram.addInString(">default=2,>min=0,>max=10", 1, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=3", IntHistogram.addInString(">default=2,>min=0,>max=10", 1, 1))

        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=0", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, -2))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=1", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, -1))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, 0))
        assertEquals(">default=2,>min=0,>max=10,>1=3,>4=10,>6=3", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=3,>4=10", 6, 1))
    }

    @Test
    fun stringFormat_add_extantValue() {
        assertEquals(">default=0,>1=11", IntHistogram.addInString(">default=0,>1=1", 1, 10))
        assertEquals(">default=0,>1=2", IntHistogram.addInString(">default=0,>1=1", 1, 1))
        assertEquals(">default=0,>1=1", IntHistogram.addInString(">default=0,>1=1", 1, 0))
        assertEquals(">default=0", IntHistogram.addInString(">default=0,>1=1", 1, -1))
        assertEquals(">default=0,>1=-1", IntHistogram.addInString(">default=0,>1=1", 1, -2))

        assertEquals(">default=0,>1=11,>2=4", IntHistogram.addInString(">default=0,>1=1,>2=4", 1, 10))
        assertEquals(">default=0,>1=2,>2=4", IntHistogram.addInString(">default=0,>1=1,>2=4", 1, 1))
        assertEquals(">default=0,>1=1,>2=4", IntHistogram.addInString(">default=0,>1=1,>2=4", 1, 0))
        assertEquals(">default=0,>2=4", IntHistogram.addInString(">default=0,>1=1,>2=4", 1, -1))
        assertEquals(">default=0,>1=-1,>2=4", IntHistogram.addInString(">default=0,>1=1,>2=4", 1, -2))

        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=20", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, 10))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=11", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, 1))
        assertEquals(">default=2,>min=0,>max=10,>1=5", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, -8))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=1", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, -9))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=0", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, -10))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=-1", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10", 2, -11))

        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=20,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, 10))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=11,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, 1))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, -8))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=1,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, -9))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=0,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, -10))
        assertEquals(">default=2,>min=0,>max=10,>1=5,>2=-1,>3=4", IntHistogram.addInString(">default=2,>min=0,>max=10,>1=5,>2=10,>3=4", 2, -11))
    }

    @Test
    fun stringFormat_add_outOfRange() {
        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.addInString(">default=0,>min=5", 1, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.addInString(">default=0,>max=5", 6, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.addInString(">default=0,>min=1,>max=5", 0, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.addInString(">default=0,>min=1,>max=5", -1, 10)
        }

        assertThrows(IllegalArgumentException::class.java) {
            IntHistogram.addInString(">default=0,>min=1,>max=5", 6, 10)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Utilities
    //---------------------------------------------------------------------------------------------
    @Test
    fun clear_empty() {
        IntHistogram().let {
            it.clear() ; it
        }.assertEquals()

        IntHistogram(min = 0).let {
            it.clear() ; it
        }.assertEquals(min = 0)

        IntHistogram(max = 0).let {
            it.clear() ; it
        }.assertEquals(max = 0)

        IntHistogram(min = -5, max = 20).let {
            it.clear() ; it
        }.assertEquals(min = -5, max = 20)

        IntHistogram(min = -5, max = 20, default = 3).let {
            it.clear() ; it
        }.assertEquals(min = -5, max = 20, default = 3)
    }

    @Test
    fun clear_populated() {
        IntHistogram.fromString(populatedMapAsString).let {
            it.clear() ; it
        }.assertEquals()

        IntHistogram.fromString("$populatedMapAsString,>min=0").let {
            it.clear() ; it
        }.assertEquals(min = 0)

        IntHistogram.fromString(">max=10,$populatedMapAsString").let {
            it.clear() ; it
        }.assertEquals(max = 10)

        IntHistogram.fromString(">min=-5,$populatedMapAsString,>max=20").let {
            it.clear() ; it
        }.assertEquals(min = -5, max = 20)

        IntHistogram.fromString(">min=-5,>max=20,$populatedMapAsString,>default=3").let {
            it.clear() ; it
        }.assertEquals(min = -5, max = 20, default = 3)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Utilities
    //---------------------------------------------------------------------------------------------
    private fun IntHistogram.insertAllIndividually(entries: Map<Int, Int>): IntHistogram {
        return insertAllIndividually(entries.toList())
    }

    private fun IntHistogram.insertAllIndividually(entries: List<Pair<Int, Int>>): IntHistogram {
        for (entry in entries) {
            this[entry.first] = entry.second
        }
        return this
    }

    private fun IntHistogram.insertAllTogether(entries: Map<Int, Int>): IntHistogram {
        this.putAll(entries)
        return this
    }

    private fun IntHistogram.insertAllTogether(entries: List<Pair<Int, Int>>): IntHistogram {
        return insertAllTogether(mapOf(*entries.toTypedArray()))
    }

    private fun IntHistogram.assertEquals(entries: Map<Int, Int> = emptyMap, min: Int? = null, max: Int? = null, default: Int = 0) {
        // Filter default values
        val realEntries = entries.filter { it.value != default }

        // Verify that the appropriate default value is present.
        assertEquals(default, this.default)

        // Verify the minimum and maximum keys for this Histogram.
        // If only one boundary is set explicitly, the other is inferred as the most extreme key
        // present among the entries.
        assertEquals(min, this.min)
        assertEquals(max, this.max)

        // Verify the expected output from [] queries for extant entries
        for (entry in entries) {
            assertEquals(entry.value, this[entry.key])
        }

        // Verify the expected output from [] queries for possibly-extant entries
        for (key in widespreadKeys) {
            val has = entries.containsKey(key)
            val value = when {
                has -> entries[key]
                min != null && max != null && min <= key && key <= max ->
                    default
                else -> 0
            }
            assertEquals(value, this[key])
        }

        // Verify size and total
        val size = if (min == null || max == null) realEntries.size else (max - min + 1)
        val total = entries.values.sum() + (size - entries.size) * default
        assertEquals(size, this.size)
        assertEquals(total, this.total)
        assertEquals(total, this.sum())
    }

    private fun String.assertEquals(entries: Map<Int, Int> = emptyMap, min: Int? = null, max: Int? = null, default: Int = 0) {
        // Verify header
        val header = buildString {
            append(">default=$default")
            if (min != null) append(",>min=$min")
            if (max != null) append(",>max=$max")
        }

        assertTrue(this.startsWith(header))

        // Filter defaults
        val includedEntries = entries.filter { it.value != default }

        // Verify each value is present
        val elements = this.split(",")
        includedEntries.forEach { assertTrue(elements.contains(">${it.key}=${it.value}")) }

        // Verify length (no extra cruft)
        val elementsLength = includedEntries.entries.sumOf { ",>${it.key}=${it.value}".length }
        assertEquals(elementsLength + header.length, this.length)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}