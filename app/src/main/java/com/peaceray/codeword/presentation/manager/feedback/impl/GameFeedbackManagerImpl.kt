package com.peaceray.codeword.presentation.manager.feedback.impl

import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackManager
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackProvider
import com.peaceray.codeword.presentation.manager.feedback.guess.HintingGuessCreator
import com.peaceray.codeword.presentation.manager.feedback.guess.VanillaGuessCreator
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class GameFeedbackManagerImpl @Inject constructor(
    private val gameCreationManager: GameCreationManager,
    @ForComputation private val computatationDispatcher: CoroutineDispatcher
): GameFeedbackManager {

    override suspend fun getGameFeedbackProvider(
        gameSetup: GameSetup,
        hints: Boolean
    ): GameFeedbackProvider {
        val policy = gameSetup.evaluation.type
        return GameFeedbackProviderImpl(
            gameSetup.evaluation.type,
            HintingGuessCreator(policy),
            InferredMarkupFeedbackProvider(
                gameCreationManager.getCodeCharacters(gameSetup).toSet(),


                gameSetup.vocabulary.length,
                gameSetup.vocabulary.characterOccurrences,
                if (hints) {
                    setOf(InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED, InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION)
                } else {
                    setOf(InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT, InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION)
                }
            ),
            computatationDispatcher
        )
    }

    override suspend fun getGameFeedbackProvider(
        gameSaveData: GameSaveData,
        hints: Boolean
    ): GameFeedbackProvider {
        return getGameFeedbackProvider(gameSaveData.setup, hints)
    }
}