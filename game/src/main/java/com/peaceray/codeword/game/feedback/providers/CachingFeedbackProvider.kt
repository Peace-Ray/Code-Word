package com.peaceray.codeword.game.feedback.providers

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.game.feedback.FeedbackProvider

abstract class CachingFeedbackProvider(val characters: Set<Char>, val length: Int, val occurrences: IntRange): FeedbackProvider {

    private var cachedInput: Pair<ConstraintPolicy, List<Constraint>>? = null
    private var cachedOutput: Pair<Feedback, Map<Char, CharacterFeedback>>? = null

    override fun getFeedback(
        policy: ConstraintPolicy,
        constraints: List<Constraint>
    ) = cachedGet(policy, constraints).first

    override fun getCharacterFeedback(
        policy: ConstraintPolicy,
        constraints: List<Constraint>
    ) = cachedGet(policy, constraints).second

    private fun cachedGet(policy: ConstraintPolicy, constraints: List<Constraint>): Pair<Feedback, Map<Char, CharacterFeedback>> {
        val previousInput: Pair<ConstraintPolicy, List<Constraint>>?
        val previousOutput: Pair<Feedback, Map<Char, CharacterFeedback>>?
        synchronized(this) {
            previousInput = cachedInput
            previousOutput = cachedOutput
        }

        // perform computation, not synchronized (does not members)
        val input = Pair(policy, constraints)
        val output = when {
            // cache hit
            input == previousInput -> previousOutput

            // cache revision
            policy == previousInput?.first && previousInput.second.all { it in constraints } ->
                constrainFeedback(
                    previousOutput!!,
                    policy,
                    constraints,
                    constraints.filter { it !in previousInput.second }
                )

            // cache miss
            else -> constrainFeedback(
                initializeFeedback(policy),
                policy,
                constraints,
                constraints
            )
        }

        // update cache
        synchronized(this) {
            cachedInput = input
            cachedOutput = output
        }

        return output!!
    }

    open fun initializeFeedback(policy: ConstraintPolicy): Pair<Feedback, Map<Char, CharacterFeedback>> {
        return Pair(
            Feedback(characters, length, occurrences),
            characters.associateWith { CharacterFeedback(it, occurrences) }
        )
    }

    abstract fun constrainFeedback(
        feedback: Pair<Feedback, Map<Char, CharacterFeedback>>,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Pair<Feedback, Map<Char, CharacterFeedback>>
}