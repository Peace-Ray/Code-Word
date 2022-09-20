package com.peaceray.codeword.presentation.view.component.viewholders.review

import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.GameStatusReview

interface GameReviewListener {

    //region Seed
    //---------------------------------------------------------------------------------------------
    fun onRandomizeSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?)
    fun onEditSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?)
    fun onCopySeedClicked(seed: String?, viewHolder: GameReviewViewHolder?)
    fun onShareSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?)
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Outcome
    //---------------------------------------------------------------------------------------------
    fun onCopyOutcomeClicked(outcome: GameOutcome, viewHolder: GameReviewViewHolder?)
    fun onShareOutcomeClicked(outcome: GameOutcome, viewHolder: GameReviewViewHolder?)
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Creation
    //---------------------------------------------------------------------------------------------
    fun onCreatePuzzleClicked(review: GameStatusReview?, viewHolder: GameReviewViewHolder?)
    //---------------------------------------------------------------------------------------------
    //endregion

}