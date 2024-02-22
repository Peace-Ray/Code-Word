package com.peaceray.codeword.game.feedback.providers

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.game.feedback.FeedbackProvider

abstract class CachingFeedbackProvider(val characters: Set<Char>, val length: Int, val occurrences: IntRange): FeedbackProvider {

    private var cachedInput: Pair<ConstraintPolicy, List<Constraint>>? = null
    private var cachedOutput: Feedback? = null

    override fun getFeedback(
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)?
    ): Feedback {
        val feedback = cachedGet(policy, constraints, callback)
        if (callback != null) callback(feedback, true)
        return feedback
    }

    private fun cachedGet(
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)?
    ): Feedback {
        val previousInput: Pair<ConstraintPolicy, List<Constraint>>?
        val previousOutput: Feedback?
        synchronized(this) {
            previousInput = cachedInput
            previousOutput = cachedOutput
        }

        // perform computation, not synchronized (does not touch members)
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
                    constraints.filter { it !in previousInput.second },
                    callback
                )

            // cache miss
            else -> {
                val init = initializeFeedback(policy)
                if (callback != null) callback(init, false)
                constrainFeedback(
                    initializeFeedback(policy),
                    policy,
                    constraints,
                    constraints,
                    callback
                )
            }
        }

        // update cache
        synchronized(this) {
            cachedInput = input
            cachedOutput = output
        }

        return output!!
    }

    open fun initializeFeedback(policy: ConstraintPolicy) = Feedback(characters, length, occurrences)

    abstract fun constrainFeedback(
        feedback: Feedback,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)?
    ): Feedback
}