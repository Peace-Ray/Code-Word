package com.peaceray.codeword.presentation.view.component.viewholders.review

import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import timber.log.Timber

/**
 * An empty implementation of GameSetupListener; extend from this if there are only a few
 * GameSetup actions relevant in your context.
 */
open class GameReviewListenerAdapter: GameReviewListener {

    //region Seed
    //---------------------------------------------------------------------------------------------
    override fun onRandomizeSeedClicked(
        seed: String?,
        viewHolder: GameReviewViewHolder?
    ) {
        Timber.w("onRandomizeSeedClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }

    override fun onEditSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
        Timber.w("onEditSeedClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }

    override fun onCopySeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
        Timber.w("onCopySeedClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }

    override fun onShareSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
        Timber.w("onShareSeedClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Outcome
    //---------------------------------------------------------------------------------------------
    override fun onCopyOutcomeClicked(outcome: GameOutcome, viewHolder: GameReviewViewHolder?) {
        Timber.w("onCopyOutcomeClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }

    override fun onShareOutcomeClicked(outcome: GameOutcome, viewHolder: GameReviewViewHolder?) {
        Timber.w("onShareOutcomeClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Creation
    //---------------------------------------------------------------------------------------------
    override fun onCreatePuzzleClicked(review: GameStatusReview?, viewHolder: GameReviewViewHolder?) {
        Timber.w("onCreatePuzzleClicked invoked in base GameSetupListenerAdapter from holder $viewHolder")
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}