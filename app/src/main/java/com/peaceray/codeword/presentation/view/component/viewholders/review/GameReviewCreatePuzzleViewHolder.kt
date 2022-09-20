package com.peaceray.codeword.presentation.view.component.viewholders.review

import android.view.View
import android.widget.TextView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager

/**
 * A ViewHolder for a "Create Puzzle" button. No other content. Although current setup and/or
 * outcome are ignored, this ViewHolder "supports" both types so it can appear in both contexts
 * without errors or warnings.
 *
 * Not intended for contexts where the creation of a puzzle is related to settings applied
 * via a GameStatusReview. In that context, the "Create" button is always available, even
 * for invalid configurations.
 */
class GameReviewCreatePuzzleViewHolder(
    itemView: View,
    colorSwatchManager: ColorSwatchManager,
    var listener: GameReviewListener? = null
):
    GameReviewViewHolder(itemView, colorSwatchManager),
    GameReviewViewHolder.SupportsGameStatusReview,
    GameReviewViewHolder.SupportsGameOutcome
{

    //region View
    //---------------------------------------------------------------------------------------------
    private val createPuzzleButton: View = itemView.findViewById(R.id.createPuzzleButton)

    init {
        createPuzzleButton.setOnClickListener {
            listener?.onCreatePuzzleClicked(review, this@GameReviewCreatePuzzleViewHolder)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Binding
    //---------------------------------------------------------------------------------------------
    override fun setViewContent(review: GameStatusReview, mutable: Boolean) {
        // ignore
    }

    override fun setViewStyle(
        review: GameStatusReview,
        mutable: Boolean,
        colorSwatch: ColorSwatch
    ) {
        // ignore
    }

    override fun setViewContent(outcome: GameOutcome) {
        // ignore
    }

    override fun setViewStyle(outcome: GameOutcome, colorSwatch: ColorSwatch) {
        // ignore
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}