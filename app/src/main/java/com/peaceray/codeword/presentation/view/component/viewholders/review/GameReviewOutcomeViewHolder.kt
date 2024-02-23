package com.peaceray.codeword.presentation.view.component.viewholders.review

import android.graphics.PorterDuff
import android.opengl.Visibility
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import java.util.*

/**
 * A ViewHolder for the "puzzle outcome" section, which specifies the winner, number of guesses,
 * and the secret (if known). Also provides "Share" and/or "Copy" buttons to share this outcome.
 *
 * The `mutable` setting is ignored.
 */
class GameReviewOutcomeViewHolder(
    itemView: View,
    colorSwatchManager: ColorSwatchManager,
    var listener: GameReviewListener? = null
):
    GameReviewViewHolder(itemView, colorSwatchManager),
    GameReviewViewHolder.SupportsGameOutcome
{

    //region View
    //---------------------------------------------------------------------------------------------
    private val outcomeTextView: TextView = itemView.findViewById(R.id.outcomeTextView)
    private val secretTextView: TextView = itemView.findViewById(R.id.secretTextView)
    private val roundsTextView: TextView = itemView.findViewById(R.id.roundsTextView)

    private val shareOutcomeButton: ImageButton? = itemView.findViewById(R.id.shareOutcomeButton)
    private val copyOutcomeButton: ImageButton? = null // itemView.findViewById(R.id.copyOutcomeButton)

    init {
        shareOutcomeButton?.setOnClickListener {
            outcome?.let { listener?.onShareOutcomeClicked(it, this@GameReviewOutcomeViewHolder) }
        }

        copyOutcomeButton?.setOnClickListener {
            outcome?.let { listener?.onCopyOutcomeClicked(it, this@GameReviewOutcomeViewHolder) }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Binding
    //---------------------------------------------------------------------------------------------
    override fun setViewContent(outcome: GameOutcome) {
        val context = itemView.context

        // outcome
        outcomeTextView.text = context.getString(when(outcome.outcome) {
            GameOutcome.Outcome.WON -> R.string.game_outcome_outcome_win
            GameOutcome.Outcome.LOST -> R.string.game_outcome_outcome_lost
            GameOutcome.Outcome.FORFEIT -> R.string.game_outcome_outcome_lost
            GameOutcome.Outcome.LOADING -> R.string.game_outcome_outcome_loading
        })

        // secret
        secretTextView.text = when(outcome.secret) {
            null -> context.getString(R.string.game_outcome_secret_null)
            else -> {
                val uppercaseSecret = outcome.secret.toUpperCase(Locale.getDefault())
                context.getString(R.string.game_outcome_secret, uppercaseSecret)
            }
        }

        // rounds
        val hintAddendum = if (outcome.hintingSinceRound < 0) {
            context.getString(R.string.game_outcome_rounds_addendum_no_hints)
        } else {
            context.getString(R.string.game_outcome_rounds_addendum_hints)
        }
        roundsTextView.text = if (outcome.rounds >= 100) {
            context.getString(R.string.game_outcome_rounds_unlimited, outcome.round, hintAddendum)
        } else when(outcome.outcome) {
            GameOutcome.Outcome.WON -> context.getString(R.string.game_outcome_rounds_won, outcome.round, outcome.rounds, hintAddendum)
            GameOutcome.Outcome.LOST -> context.getString(R.string.game_outcome_rounds_lost, outcome.rounds, hintAddendum)
            GameOutcome.Outcome.FORFEIT -> context.getString(R.string.game_outcome_rounds_forfeit, outcome.round, outcome.rounds, hintAddendum)
            GameOutcome.Outcome.LOADING -> context.getString(R.string.game_outcome_rounds_loading, outcome.rounds)
        }
    }

    override fun setViewStyle(outcome: GameOutcome, colorSwatch: ColorSwatch) {
        outcomeTextView.setTextColor(when(outcome.outcome) {
            GameOutcome.Outcome.WON -> colorSwatch.evaluation.exact
            GameOutcome.Outcome.LOST -> colorSwatch.container.onBackground
            GameOutcome.Outcome.FORFEIT -> colorSwatch.evaluation.included
            GameOutcome.Outcome.LOADING -> colorSwatch.container.onBackground
        })

        copyOutcomeButton?.setColorFilter(colorSwatch.container.onBackground, PorterDuff.Mode.MULTIPLY)
        shareOutcomeButton?.setColorFilter(colorSwatch.container.onBackground, PorterDuff.Mode.MULTIPLY)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}