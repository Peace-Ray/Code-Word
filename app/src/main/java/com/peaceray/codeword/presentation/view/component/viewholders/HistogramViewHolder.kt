package com.peaceray.codeword.presentation.view.component.viewholders

import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.HistogramEntry
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.utils.extensions.toLifecycleOwner
import timber.log.Timber

class HistogramViewHolder(
    itemView: View,
    val layoutInflater: LayoutInflater,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    private val constraintLayout: ConstraintLayout?
    private val backgroundView: View
    private val roundTextView: TextView
    private val countTextView: TextView

    private val histogramBiasView: View
    private val histogramBackgroundView: View

    init {
        constraintLayout = itemView.findViewById(R.id.constraintLayout)
        backgroundView = itemView.findViewById(R.id.backgroundView) ?: itemView
        roundTextView = itemView.findViewById(R.id.roundTextView)
        countTextView = itemView.findViewById(R.id.countTextView)

        histogramBiasView = itemView.findViewById(R.id.histogramBiasView)
        histogramBackgroundView = itemView.findViewById(R.id.histogramBackgroundView)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var bound = false

    var entry: HistogramEntry? = null
        private set

    var outcome: GameOutcome? = null
        private set

    var max: Int = 0
        private set

    fun bind(entry: HistogramEntry, outcome: GameOutcome?, max: Int? = null) {
        this.entry = entry
        this.outcome = outcome
        this.max = max ?: entry.histogram.total

        setViewContent(entry)
        setViewColors(entry, outcome, colorSwatchManager.colorSwatch)

        bound = true
    }

    private fun setViewContent(entry: HistogramEntry) {
        // round number
        roundTextView.text = itemView.context.getString(when(entry.span) {
            HistogramEntry.Span.EQ -> R.string.game_outcome_round_histogram_eq
            HistogramEntry.Span.NEQ -> R.string.game_outcome_round_histogram_neq
            HistogramEntry.Span.LT -> R.string.game_outcome_round_histogram_lt
            HistogramEntry.Span.GT -> R.string.game_outcome_round_histogram_gt
            HistogramEntry.Span.LTE -> R.string.game_outcome_round_histogram_lte
            HistogramEntry.Span.GTE -> R.string.game_outcome_round_histogram_gte
        }, entry.key)

        // result count
        countTextView.text = "${entry.value}"

        // histogram bar position
        if (constraintLayout != null) {
            val bias = if (max == 0) 0.0f else entry.value.toFloat() / max
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)
            constraintSet.setHorizontalBias(histogramBiasView.id, bias)             // left-to-right
            constraintSet.setVerticalBias(histogramBiasView.id, 1.0f - bias)   // bottom-to-top
            constraintSet.applyTo(constraintLayout)
        }
    }

    private fun setViewColors(entry: HistogramEntry, outcome: GameOutcome?, swatch: ColorSwatch) {
        val isOutcome = outcome?.outcome == GameOutcome.Outcome.WON && entry.includesKey(outcome.round)
        val markup = when {
            isOutcome -> Constraint.MarkupType.EXACT
            entry.value == 0 -> Constraint.MarkupType.NO
            else -> Constraint.MarkupType.INCLUDED
        }
        histogramBackgroundView.setBackgroundColor(swatch.evaluation.color(markup))
        countTextView.setTextColor(swatch.evaluation.onColor(markup))
        roundTextView.setTextColor(swatch.container.onBackground)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}