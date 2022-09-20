package com.peaceray.codeword.presentation.view.component.viewholders

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.LayoutRes
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.views.PipGridLayout
import com.peaceray.codeword.utils.extensions.toLifecycleOwner

class ConstraintPipGridViewHolder(
    itemView: View,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    private val pipGrid: PipGridLayout = itemView.findViewById(R.id.pipGridLayout)
    private val noPipImageView: ImageView = itemView.findViewById(R.id.noPipImageView)
    private val constraintPipViews = mutableListOf<View>()

    private val dimenPipElevation: Float

    init {
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
        val lengthChanged = _guess.length != guess.length
        _guess = guess

        // configure pip layout
        if (lengthChanged) {
            pipGrid.pipCount = _guess.length
            refreshPipViews()
        }

        // configure pip/nopip visibility
        if (guess.constraint == null) {
            pipGrid.visibility = View.GONE
            noPipImageView.visibility = View.GONE
        } else if (guess.constraint.exact + guess.constraint.included == 0) {
            pipGrid.visibility = View.GONE
            noPipImageView.visibility = View.VISIBLE
        } else {
            pipGrid.visibility = View.VISIBLE
            noPipImageView.visibility = View.GONE
        }

        // configure pip visible count
        pipGrid.setPipsVisibility(if (guess.constraint == null) 0 else {
            guess.constraint.exact + guess.constraint.included
        })

        // configure pip colors
        setConstraintColors(guess, colorSwatchManager.colorSwatch)
    }

    private fun refreshPipViews() {
        constraintPipViews.clear()
        for (i in 0 until pipGrid.pipCount) {
            val child = pipGrid.getChildAt(i)
            val pip = child.findViewById(R.id.pipView) ?: child
            constraintPipViews.add(pip)
        }
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

        noPipImageView.setColorFilter(swatch.evaluation.noVariant, PorterDuff.Mode.MULTIPLY)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}