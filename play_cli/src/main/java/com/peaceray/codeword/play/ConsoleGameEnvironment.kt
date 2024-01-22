package com.peaceray.codeword.play

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.data.Settings
import com.peaceray.codeword.game.feedback.ConstraintFeedbackPolicy
import com.peaceray.codeword.game.feedback.FeedbackProvider
import com.peaceray.codeword.game.validators.Validator

class ConsoleGameEnvironment(
    val game: Game,
    val solver: Solver,
    val evaluator: Evaluator,
    val constraintFeedbackPolicy: ConstraintFeedbackPolicy,
    val feedbackProvider: FeedbackProvider? = null,
) {
    fun reset() {
        game.reset()
        evaluator.reset()
    }

    class Builder {
        var length: Int? = null
        var rounds: Int? = null
        var policy: ConstraintPolicy = ConstraintPolicy.IGNORE
        var validator: Validator? = null

        var solver: Solver? = null
        var evaluator: Evaluator? = null

        var feedbackProvider: FeedbackProvider? = null
        var constraintFeedbackPolicy: ConstraintFeedbackPolicy? = null

        fun withDimensions(length: Int, rounds: Int): Builder {
            this.length = length
            this.rounds = rounds
            return this
        }

        fun withConstraintPolicy(policy: ConstraintPolicy): Builder {
            this.policy = policy
            return this
        }

        fun withValidator(validator: Validator): Builder {
            this.validator = validator
            return this
        }

        fun withSolver(solver: Solver): Builder {
            this.solver = solver
            return this
        }

        fun withEvaluator(evaluator: Evaluator): Builder {
            this.evaluator = evaluator
            return this
        }

        fun withFeedbackProvider(feedbackProvider: FeedbackProvider): Builder {
            this.feedbackProvider = feedbackProvider
            return this
        }

        fun withConstraintFeedbackPolicy(feedbackPolicy: ConstraintFeedbackPolicy): Builder {
            this.constraintFeedbackPolicy = feedbackPolicy
            return this
        }

        fun build(): ConsoleGameEnvironment {
            return ConsoleGameEnvironment(
                Game(Settings(length!!, rounds!!, policy), validator!!),
                solver!!,
                evaluator!!,
                constraintFeedbackPolicy!!,
                feedbackProvider
            )
        }
    }
}