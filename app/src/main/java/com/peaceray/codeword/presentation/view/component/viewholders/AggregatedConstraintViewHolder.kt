package com.peaceray.codeword.presentation.view.component.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager

class AggregatedConstraintViewHolder(
    itemView: View,
    val layoutInflater: LayoutInflater,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    @LayoutRes var layoutId: Int = R.layout.cell_pip_large

    private val constraintContainer: ViewGroup = itemView.findViewById(R.id.constraintContainer)
    private val constraintPipViews = mutableListOf<View>()

    private val dimenPipElevation: Float

    init {
        constraintContainer.children.forEachIndexed { index, view ->
            val v = view.findViewById<View>(R.id.pipView)
            if (v != null) constraintPipViews.add(v)
        }

        val context = itemView.context
        dimenPipElevation = context.resources.getDimension(R.dimen.aggregate_markup_pip_elevation)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var _guess = Guess.placeholder
    var guess
        get() = _guess
        set(value) = bind(value)

    fun bind(guess: Guess) {
        _guess = guess

        // extend pips
        while (constraintContainer.childCount < guess.length) {
            val view = layoutInflater.inflate(layoutId, constraintContainer, true)
            constraintPipViews.add(view.findViewById(R.id.pipView))
        }

        // adjust visibility
        constraintContainer.children.forEachIndexed { index, view ->
            val visibility = if (index < guess.length) View.VISIBLE else View.GONE
            view.visibility = visibility
        }

        setConstraintColors(guess, colorSwatchManager.colorSwatch)
    }

    private fun setConstraintColors(guess: Guess, swatch: ColorSwatch) {
        val exact = guess.constraint?.exact ?: 0
        val inclu = guess.constraint?.included ?: 0
        List(guess.length) {
            when {
                it < exact -> Constraint.MarkupType.EXACT
                it < exact + inclu -> Constraint.MarkupType.INCLUDED
                else -> Constraint.MarkupType.NO
            }
        }.zip(constraintPipViews).forEach { (markUp, pipView) ->
            val bg = if (!guess.isEvaluation) swatch.container.background else swatch.evaluation.color(markUp)
            val stroke = if (!guess.isEvaluation) swatch.evaluation.untried else bg
            val elevation = if (!guess.isEvaluation) 0.0f else dimenPipElevation

            if (pipView is MaterialCardView) {
                pipView.setCardBackgroundColor(bg)
                pipView.strokeColor = stroke
                pipView.elevation = elevation
            } else {
                pipView.setBackgroundColor(bg)
                pipView.elevation = elevation
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}