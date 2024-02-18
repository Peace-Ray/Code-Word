package com.peaceray.codeword.presentation.view.component.viewholders.guess

import android.graphics.PorterDuff
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import androidx.core.view.setMargins
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedAppearance
import com.peaceray.codeword.presentation.view.component.views.PipGridLayout
import kotlin.math.round

class GuessAggregatedPipGridViewHolder(
    itemView: View,
    val colorSwatchManager: ColorSwatchManager,
    var appearance: GuessAggregatedAppearance
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    private val pipGrid: PipGridLayout = itemView.findViewById(R.id.pipGridLayout)
    private val noPipImageView: ImageView = itemView.findViewById(R.id.noPipImageView)
    private val constraintPipViews = mutableListOf<View>()
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var _evaluation: GuessEvaluation? = null
    var evaluation
        get() = _evaluation
        set(value) = bind(value)

    fun bind(eval: GuessEvaluation?) {
        val lengthChanged = _evaluation?.length != eval?.length
        _evaluation = eval

        // configure pip layout
        if (lengthChanged) {
            pipGrid.pipCount = _evaluation?.length ?: 0
            refreshPipViews()
        }

        // configure pip/nopip visibility
        if (eval == null) {
            pipGrid.visibility = View.GONE
            noPipImageView.visibility = View.GONE
        } else if (eval.total == 0) {
            pipGrid.visibility = View.GONE
            noPipImageView.visibility = View.VISIBLE
        } else {
            pipGrid.visibility = View.VISIBLE
            noPipImageView.visibility = View.GONE
        }

        // configure pip visible count
        pipGrid.setPipsVisibility(eval?.total ?: 0)

        // configure pip details
        setConstraintDetails(eval, colorSwatchManager.colorSwatch)
    }

    private fun refreshPipViews() {
        constraintPipViews.clear()
        for (i in 0 until pipGrid.pipCount) {
            val child = pipGrid.getChildAt(i)
            val pip = child.findViewById(R.id.pipView) ?: child
            constraintPipViews.add(pip)
        }

        if (appearance.hasStableDimensions) {
            constraintPipViews.forEach { view ->
                view.updateLayoutParams {
                    val size = round(appearance.getPipSize(evaluation, null)).toInt()
                    width = size
                    height = size
                    if (this is MarginLayoutParams) {
                        setMargins(round(appearance.getPipMargin(evaluation, null)).toInt())
                    }
                }
            }
        }
    }

    private fun setConstraintDetails(eval: GuessEvaluation?, swatch: ColorSwatch) {
        val exact = eval?.exact ?: 0
        val inclu = eval?.included ?: 0
        List(eval?.length ?: 0) {
            when {
                it < exact -> Constraint.MarkupType.EXACT
                it < exact + inclu -> Constraint.MarkupType.INCLUDED
                else -> Constraint.MarkupType.NO
            }
        }.zip(constraintPipViews).forEach { (markUp, pipView) ->
            // dimensions
            if (!appearance.hasStableDimensions) {
                pipView.updateLayoutParams {
                    val size = appearance.getPipSize(eval, markUp).toInt()
                    width = size
                    height = size
                    if (this is MarginLayoutParams) {
                        setMargins(appearance.getPipMargin(eval, markUp).toInt())
                    }
                }
            }

            // colors / details
            if (pipView is MaterialCardView) {
                pipView.radius = appearance.getPipCornerRadius(eval, markUp)
                pipView.setCardBackgroundColor(appearance.getColorFill(eval, markUp, swatch))
                pipView.strokeColor = appearance.getColorStroke(eval, markUp, swatch)
                pipView.strokeWidth = appearance.getPipStrokeWidth(eval, markUp)
                pipView.elevation = appearance.getPipElevation(eval, markUp)
            } else {
                pipView.setBackgroundColor(appearance.getColorFill(eval, markUp, swatch))
                pipView.elevation = appearance.getPipElevation(eval, markUp)
            }
        }

        noPipImageView.setColorFilter(swatch.evaluation.noVariant, PorterDuff.Mode.MULTIPLY)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}