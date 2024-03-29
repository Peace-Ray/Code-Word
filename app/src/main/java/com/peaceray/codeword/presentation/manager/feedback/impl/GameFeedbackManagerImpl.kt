package com.peaceray.codeword.presentation.manager.feedback.impl

import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.model.game.save.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import com.peaceray.codeword.glue.ForComputation
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackManager
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackProvider
import com.peaceray.codeword.presentation.manager.feedback.guess.EliminationGuessCreator
import com.peaceray.codeword.presentation.manager.feedback.guess.HintingGuessCreator
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class GameFeedbackManagerImpl @Inject constructor(
    private val gameCreationManager: GameCreationManager,
    @ForComputation private val computatationDispatcher: CoroutineDispatcher
): GameFeedbackManager {

    //region GameFeedbackProvider Creation
    //-----------------------------------------------------------------------------------------

    override suspend fun getGameFeedbackProvider(
        gameSetup: GameSetup,
        hints: Boolean
    ): GameFeedbackProvider {
        val policy = gameSetup.evaluation.type
        val markupPolicies = if (hints) {
            setOf(
                InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED,
                InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT,
                InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION
            )
        } else {
            setOf(
                InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT
            )
        }

        val guessCreator = if (hints || gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
            HintingGuessCreator(policy)
        } else {
            EliminationGuessCreator(policy)
        }

        return GameFeedbackProviderImpl(
            gameSetup.evaluation.type,
            guessCreator,
            InferredMarkupFeedbackProvider(
                gameCreationManager.getCodeCharacters(gameSetup).toSet(),
                gameSetup.vocabulary.length,
                gameSetup.vocabulary.characterOccurrences,
                markupPolicies
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

    //-----------------------------------------------------------------------------------------
    //endregion

    //region GameSetup Evaluation
    //-----------------------------------------------------------------------------------------
    override fun supportsHinting(gameSetup: GameSetup): Boolean {
        // hinting is not support for daily challenges, or games with by-letter markup.
        return !gameSetup.daily && !gameSetup.evaluation.type.isByLetter()
    }

    override fun supportsHinting(gameSaveData: GameSaveData): Boolean {
        return supportsHinting(gameSaveData.setup)
    }

    //-----------------------------------------------------------------------------------------
    //endregion
}