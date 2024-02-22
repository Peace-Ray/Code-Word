package com.peaceray.codeword.presentation.manager.feedback.impl

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.game.feedback.FeedbackProvider
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackProvider
import com.peaceray.codeword.presentation.manager.feedback.guess.GuessCreator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject

class GameFeedbackProviderImpl @Inject constructor(
    private val constraintPolicy: ConstraintPolicy,
    private val guessCreator: GuessCreator,
    private val feedbackProvider: FeedbackProvider,
    @ForComputation private val computationDispatcher: CoroutineDispatcher
): GameFeedbackProvider {

    //region Feedback / Character Feedback
    //---------------------------------------------------------------------------------------------

    override suspend fun getFeedback(constraints: List<Constraint>): Feedback {
        return withContext(computationDispatcher) {
            // provide a suspension callback: accept a pending result if the job is not active.
            val acceptOnCancelCallback = { _: Feedback, _: Boolean -> !isActive }
            feedbackProvider.getFeedback(constraintPolicy, constraints, acceptOnCancelCallback)
        }
    }

    override fun getFeedbackFlow(constraints: List<Constraint>): Flow<Pair<Feedback, Boolean>> {
        return callbackFlow {
            val flowOnCallback = { feedback: Feedback, done: Boolean ->
                trySend(Pair(feedback, done))
                done || !isActive
            }
            feedbackProvider.getFeedback(constraintPolicy, constraints, flowOnCallback)
            // only reach here after the final Feedback has been created and provided
            // (or if canceled)
        }.flowOn(computationDispatcher)
    }

    override fun getPlaceholderFeedback(): Feedback {
        return feedbackProvider.getFeedback(constraintPolicy, emptyList())
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Update Flow
    //---------------------------------------------------------------------------------------------

    override fun toGuess(partialGuess: String, feedback: Feedback) = guessCreator.toGuess(partialGuess, feedback)

    override fun toGuess(constraint: Constraint, feedback: Feedback) = guessCreator.toGuess(constraint, feedback)
    override fun toGuessAlphabet(feedback: Feedback) = guessCreator.toGuessAlphabet(feedback)

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Update Flow
    //---------------------------------------------------------------------------------------------
    override suspend fun toGuesses(constraints: List<Constraint>, feedback: Feedback): List<Guess> {
        return withContext(computationDispatcher) {
            constraints.map {
                yield()
                guessCreator.toGuess(it, feedback)
            }
        }
    }

    override fun toGuessesFlow(constraints: List<Constraint>, feedback: Feedback, reverse: Boolean): Flow<Pair<Int, Guess>> {
        return flow {
            val indices = if (reverse) constraints.indices.reversed() else constraints.indices
            indices.forEach { index ->
                emit(Pair(index, guessCreator.toGuess(constraints[index], feedback)))
            }
        }.flowOn(computationDispatcher)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}