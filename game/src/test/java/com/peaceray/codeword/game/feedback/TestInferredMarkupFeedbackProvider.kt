package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import org.junit.Test
import org.junit.Assert.*

class TestInferredMarkupFeedbackProvider {
    companion object {
        val CHARS_AF = ('A'..'F').toList()
        val CHARS_AZ = ('A'..'Z').toList()
    }

    private fun testFeedback(
        secret: String,
        guesses: List<String>,
        characters: Collection<Char>,
        lettersRepeat: Boolean,
        policy: ConstraintPolicy,
        vararg providerPolicies: InferredMarkupFeedbackProvider.MarkupPolicy
    ) = testFeedback(secret, guesses, characters, lettersRepeat, policy, providerPolicies.toSet())

    private fun testFeedback(
        secret: String,
        guesses: List<String>,
        characters: Collection<Char>,
        lettersRepeat: Boolean,
        policy: ConstraintPolicy,
        providerPolicies: Set<InferredMarkupFeedbackProvider.MarkupPolicy>
    ): Feedback {
        val length = secret.length
        val maxOccurrences = if (lettersRepeat) length else 1
        val constraints = guesses.map { Constraint.create(it, secret) }
        val provider = InferredMarkupFeedbackProvider(
            characters.toSet(),
            length,
            maxOccurrences,
            providerPolicies
        )
        val feedback = constraints.indices.map {
            val constraintsHere = constraints.subList(0, it + 1)
            val feedback = provider.getFeedback(policy, constraintsHere)

            // verify secret is possible, given feedback
            assert(feedback.allows(secret))
            // provide
            feedback
        }.last()

        if (secret == guesses.last()) {
            // verify solution is only possible word under feedback
            assertEquals(secret.toList().map { setOf(it) }, feedback.candidates)
            assertEquals(characters.associateWith { char ->
                val count = secret.count { it == char }
                count..count
            }, feedback.occurrences)
        }

        return feedback
    }

    //region Feedback Tests
    //---------------------------------------------------------------------------------------------

    @Test
    fun constraint_testProvideFeedback_aggregated_eliminateLeftovers_1() {
        val feedback = testFeedback(
            "STOAT",
            listOf("SIZES", "RUINS"),
            CHARS_AZ,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED
        )

        // letter "S" should not be a candidate in any position but 0
        for (i in 0..4) {
            val included = i == 0
            assertEquals(included, 'S' in feedback.candidates[i])
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Regression Tests
    //---------------------------------------------------------------------------------------------
    @Test
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_1() {
        testFeedback(
            "ABCBD",
            listOf(
                "AAAAB",
                "BBBCA",
                "CBBDA",
                "BCDAB",
                "DABBC",
                "ADCBB",
                "ADBCB",
                "ABCBD"
            ),
            CHARS_AF,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION_ELIMINATION
        )
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_2() {
        testFeedback(
            "TEAR",
            listOf(
                "HIKE",
                "BIKE",
                "PLAY",
                "CLAY",
                "TEAR"
            ),
            CHARS_AZ,
            false,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED
        )
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_3() {
        testFeedback(
            "PENCE",
            listOf("TREES", "SHOUT", "RELAY", "PENCE"),
            CHARS_AZ,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
            InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
        )
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_incorrect_regression_1() {
        val feedback = testFeedback(
            "MATCH",
            listOf("PIOUS", "LEARN", "GHOST", "MIGHT", "FIGHT"),
            CHARS_AZ,
            false,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED
        )

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

    @Test
    fun constraint_testProvideFeedback_aggregated_incorrect_regression_2() {
        testFeedback(
            "PENCE",
            listOf("TREES", "SHOUT", "RELAY"),
            CHARS_AZ,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
            InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
        )
    }

    @Test
    fun constraint_testProvideFeedback_aggregated_incorrect_regression_3() {
        testFeedback(
            "FLARE",
            listOf("TREES", "SHOUT", "RELAY", "BAGGY"),
            CHARS_AZ,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
            InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}