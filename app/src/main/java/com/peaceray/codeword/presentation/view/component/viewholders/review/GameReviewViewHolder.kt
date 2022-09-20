package com.peaceray.codeword.presentation.view.component.viewholders.review

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import timber.log.Timber

abstract class GameReviewViewHolder(
    itemView: View,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region Data
    //---------------------------------------------------------------------------------------------
    var review: GameStatusReview? = null
        private set

    var outcome: GameOutcome? = null
        private set

    var mutable: Boolean? = null
        private set

    fun bind(outcome: GameOutcome) {
        this.review = null
        this.outcome = outcome
        this.mutable = false

        if (this is SupportsGameOutcome) {
            setViewContent(outcome)
            setViewStyle(outcome, colorSwatchManager.colorSwatch)
        } else {
            Timber.w("Possible misuse; bound to GameOutcome but does not implement SupportsGameOutcome")
        }
    }

    fun bind(review: GameStatusReview? = this.review, mutable: Boolean? = this.mutable) {
        this.review = review
        this.mutable = mutable
        if (review != null) outcome = null

        if (this is SupportsGameStatusReview) {
            if (review != null && mutable != null) {
                setViewContent(review, mutable)
                setViewStyle(review, mutable, colorSwatchManager.colorSwatch)
            }
        } else if (review != null) {
            Timber.w("Possible misuse; bound to GameStatusReview but does not implement SupportsGameStatusReview")
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Content
    //---------------------------------------------------------------------------------------------
    interface SupportsGameStatusReview {
        fun setViewContent(review: GameStatusReview, mutable: Boolean)
        fun setViewStyle(review: GameStatusReview, mutable: Boolean, colorSwatch: ColorSwatch)
    }

    interface SupportsGameOutcome {
        fun setViewContent(outcome: GameOutcome)
        fun setViewStyle(outcome: GameOutcome, colorSwatch: ColorSwatch)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}