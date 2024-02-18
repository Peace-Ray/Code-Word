package com.peaceray.codeword.presentation.manager.feedback.impl

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.game.feedback.FeedbackProvider
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackProvider
import com.peaceray.codeword.presentation.manager.feedback.guess.GuessCreator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
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
            feedbackProvider.getFeedback(constraintPolicy, constraints)
        }
    }

    override suspend fun getCharacterFeedback(constraints: List<Constraint>): Map<Char, CharacterFeedback> {
        return withContext(computationDispatcher) {
            feedbackProvider.getCharacterFeedback(constraintPolicy, constraints)
        }
    }

    override suspend fun getFullFeedback(constraints: List<Constraint>): Pair<Feedback, Map<Char, CharacterFeedback>> {
        return withContext(computationDispatcher) {
            Pair(
                feedbackProvider.getFeedback(constraintPolicy, constraints),
                feedbackProvider.getCharacterFeedback(constraintPolicy, constraints)
            )
        }
    }

    override fun getFullPlaceholderFeedback(): Pair<Feedback, Map<Char, CharacterFeedback>> {
        return Pair(
            feedbackProvider.getFeedback(constraintPolicy, emptyList()),
            feedbackProvider.getCharacterFeedback(constraintPolicy, emptyList())
        )
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Update Flow
    //---------------------------------------------------------------------------------------------

    override fun toGuess(partialGuess: String, feedback: Feedback) = guessCreator.toGuess(partialGuess, feedback)

    override fun toGuess(constraint: Constraint, feedback: Feedback) = guessCreator.toGuess(constraint, feedback)

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Update Flow
    //---------------------------------------------------------------------------------------------

    override fun toGuesses(constraints: List<Constraint>, feedback: Feedback, reverse: Boolean): Flow<Pair<Int, Guess>> {
        return flow {
            val indices = if (reverse) constraints.indices.reversed() else constraints.indices
            indices.forEach { index ->
                emit(Pair(index, guessCreator.toGuess(constraints[index], feedback)))
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}