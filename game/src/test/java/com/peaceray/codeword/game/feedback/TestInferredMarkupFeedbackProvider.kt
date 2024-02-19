package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import org.junit.Test
import org.junit.Assert.*

class TestInferredMarkupFeedbackProvider {
    @Test
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_1() {
        val secret = "ABCBD"
        val constraints = listOf(
            "AAAAB",
            "BBBCA",
            "CBBDA",
            "BCDAB",
            "DABBC",
            "ADCBB",
            "ADBCB",
            "ABCBD"
        ).map { Constraint.create(it, secret) }

        // iterate through constraint sublists to simulate gameplay and trigger caching behavior
        val characters = ('A'..'F').toList()
        val provider = InferredMarkupFeedbackProvider(characters.toSet(), 5, 5, setOf(InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION_ELIMINATION))
        val feedback = constraints.indices.map {
            val constraintsHere = constraints.subList(0, it + 1)
            provider.getFeedback(ConstraintPolicy.AGGREGATED, constraintsHere)
        }.last()

        // if we reach this point, the bug (infinite loop) did not occur. Verify correct output.
        assertEquals(secret.toList().map { setOf(it) }, feedback.candidates)
        assertEquals(characters.associateWith { char ->
            val count = secret.count { it == char }
            count..count
        }, feedback.occurrences)
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_2() {
        val secret = "TEAR"
        val constraints = listOf(
            "HIKE",
            "BIKE",
            "PLAY",
            "CLAY",
            "TEAR"
        ).map { Constraint.create(it, secret) }

        // iterate through constraint sublists to simulate gameplay and trigger caching behavior
        val characters = ('A'..'Z').toList()
        val provider = InferredMarkupFeedbackProvider(characters.toSet(), 4, 1, setOf(InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED))
        val feedback = constraints.indices.map {
            val constraintsHere = constraints.subList(0, it + 1)
            provider.getFeedback(ConstraintPolicy.AGGREGATED, constraintsHere)
        }.last()

        // if we reach this point, the bug (infinite loop) did not occur. Verify correct output.
        assertEquals(secret.toList().map { setOf(it) }, feedback.candidates)
        assertEquals(characters.associateWith { char ->
            val count = secret.count { it == char }
            count..count
        }, feedback.occurrences)
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_incorrect_regression_1() {
        val secret = "MATCH"
        val constraints = listOf(
            "PIOUS",
            "LEARN",
            "GHOST",
            "MIGHT",
            "FIGHT"
        ).map { Constraint.create(it, secret) }

        // iterate through constraint sublists to simulate gameplay and trigger caching behavior
        val characters = ('A'..'Z').toList()
        val provider = InferredMarkupFeedbackProvider(characters.toSet(), 5, 1, setOf(InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED))
        val feedback = constraints.indices.map {
            val constraintsHere = constraints.subList(0, it + 1)
            provider.getFeedback(ConstraintPolicy.AGGREGATED, constraintsHere)
        }.last()

        // verify that 'M' is known to be EXACT at position 0
        assertEquals(setOf('M'), feedback.candidates[0])
        assertEquals(1..1, feedback.occurrences['M'])
        assertEquals(Constraint.MarkupType.EXACT, feedback.characters['M']?.markup)

        // verify that A, E, L, N, and R have 'null' markup and 0..1 occurrences
        val nullChars = listOf('A', 'E', 'L', 'N', 'R')
        nullChars.forEach {  char ->
            assertEquals(0..1, feedback.occurrences[char])
            assertEquals(null, feedback.characters[char]?.markup)
        }
    }
}