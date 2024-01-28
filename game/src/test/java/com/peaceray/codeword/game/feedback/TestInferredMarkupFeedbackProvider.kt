package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import org.junit.Test
import org.junit.Assert.*

class TestInferredMarkupFeedbackProvider {
    @Test
    fun constraint_testProvideFeedback_aggregated_regression_1() {
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
        val provider = InferredMarkupFeedbackProvider(characters.toSet(), 5, 5, setOf())
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
}