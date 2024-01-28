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
        synchronized(this) {
            val previousInput = cachedInput
            cachedInput = Pair(policy, constraints)
            cachedOutput = when {
                // cache hit
                cachedInput == previousInput -> cachedOutput

                // cache revision
                policy == previousInput?.first && previousInput.second.all { it in constraints } ->
                    constrainFeedback(
                        cachedOutput!!,
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

            return cachedOutput!!
        }
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