package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import org.junit.Test
import org.junit.Assert.*

class TestInferredMarkupFeedbackProvider {
    companion object {
        val CHARS_AE = ('A'..'E').toList()
        val CHARS_AF = ('A'..'F').toList()
        val CHARS_AH = ('A'..'H').toList()
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
        vararg providerPolicies: Set<InferredMarkupFeedbackProvider.MarkupPolicy>
    ): List<Feedback> {
        val length = secret.length
        val maxOccurrences = if (lettersRepeat) length else 1
        val constraints = guesses.map { Constraint.create(it, secret) }
        val providers = providerPolicies.map {
            InferredMarkupFeedbackProvider(
                characters.toSet(),
                length,
                maxOccurrences,
                it
            )
        }

        val feedbacks = constraints.indices.map {
            val constraintsHere = constraints.subList(0, it + 1)
            val feedbacks = providers.map { provider -> provider.getFeedback(policy, constraintsHere) }

            // verify secret is possible, given feedback
            assert(feedbacks.all {  feedback -> feedback.allows(secret) })
            // provide
            feedbacks
        }.last()

        if (secret == guesses.last()) {
            // verify solution is only possible word under feedback
            feedbacks.forEach { feedback ->
                assertEquals(secret.toList().map { setOf(it) }, feedback.candidates)
                assertEquals(characters.associateWith { char ->
                    val count = secret.count { it == char }
                    count..count
                }, feedback.occurrences)
            }
        }

        return feedbacks
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
        ).first()

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
    fun constraint_testProvideFeedback_aggregated_infiniteLoop_regression_4() {
        testFeedback(
            "RAZOR",
            listOf("PIOUS", "SPITE", "LANCE", "QUERY", "CLUNK", "RADON", "WAGON", "YAHOO"),
            CHARS_AZ,
            true,
            ConstraintPolicy.AGGREGATED,
            setOf(InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT),
            setOf(InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED),
            setOf(
                InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
                InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT,
                InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
            )
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
        ).first()

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

    @Test
    fun constraint_testProvideFeedback_aggregated_incorrect_regression_4() {
        val secret = "BADD"
        val guesses = listOf("AECD", "AEEB", "DECA")
        // for secret BADD, try:
        // guess 0: AECD (E I)
        // guess 1: AEEB (I I)
        // guess 2: DECA (I I)
        // guesses 1 and 2 show that AEC_ has no exact matches, revealing that the exact match
        // in guess 0 must be ___D.
        val feedback = testFeedback(
            secret,
            guesses,
            CHARS_AF,
            true,
            ConstraintPolicy.AGGREGATED,
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED
        ).first()

        // verify that D must be the final letter.
        assertEquals(setOf('D'), feedback.candidates[3])
        assert(3 in (feedback.characters['D']?.positions ?: emptySet<Char>())) { "'D' position not indicated" }

        val constraints = guesses.map { Constraint.create(it, secret) }
        val policies = setOf(
            InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
            InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT,
            InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
        )

        // try another method: provide all constraints in one go
        InferredMarkupFeedbackProvider(
            CHARS_AF.toSet(),
            4,
            4,
            policies
        ).let { provider ->
            val f = provider.getFeedback(ConstraintPolicy.AGGREGATED, constraints)

            // verify that D must be the final letter.
            assertEquals(setOf('D'), f.candidates[3])
            assert(3 in (f.characters['D']?.positions ?: emptySet<Char>())) { "'D' position not indicated" }
        }

        // another method: provide zero-length constraints first, then iterate up
        InferredMarkupFeedbackProvider(
            CHARS_AF.toSet(),
            4,
            4,
            policies
        ).let { provider ->
            constraints.indices.forEach { index ->
                provider.getFeedback(ConstraintPolicy.AGGREGATED, constraints.subList(0, index))
            }
            val f = provider.getFeedback(ConstraintPolicy.AGGREGATED, constraints)

            // verify that D must be the final letter.
            assertEquals(setOf('D'), f.candidates[3])
            assert(3 in (f.characters['D']?.positions ?: emptySet<Char>())) { "'D' position not indicated" }
        }

        // one more method: try the first two constraints, then apply the third (to test caching)
        InferredMarkupFeedbackProvider(
            CHARS_AF.toSet(),
            4,
            4,
            policies
        ).let { provider ->
            provider.getFeedback(ConstraintPolicy.AGGREGATED, constraints.subList(0, 2))
            val f = provider.getFeedback(ConstraintPolicy.AGGREGATED, constraints)

            // verify that D must be the final letter.
            assertEquals(setOf('D'), f.candidates[3])
            assert(3 in (f.characters['D']?.positions ?: emptySet<Char>())) { "'D' position not indicated" }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}