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
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedAppearance
import com.peaceray.codeword.presentation.view.component.views.PipGridLayout
import timber.log.Timber
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
        } else if (appearance.getTotalCount(guess) == 0) {
            pipGrid.visibility = View.GONE
            noPipImageView.visibility = View.VISIBLE
        } else {
            pipGrid.visibility = View.VISIBLE
            noPipImageView.visibility = View.GONE
        }

        // configure pip visible count
        pipGrid.setPipsVisibility(if (guess.constraint == null) 0 else appearance.getTotalCount(guess))

        // configure pip details
        setConstraintDetails(guess, colorSwatchManager.colorSwatch)
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
                    val size = round(appearance.getPipSize(guess, null)).toInt()
                    width = size
                    height = size
                    if (this is MarginLayoutParams) {
                        setMargins(round(appearance.getPipMargin(guess, null)).toInt())
                    }
                }
            }
        }
    }

    private fun setConstraintDetails(guess: Guess, swatch: ColorSwatch) {
        val exact = appearance.getExactCount(guess)
        val inclu = appearance.getIncludedCount(guess)
        List(guess.length) {
            when {
                it < exact -> Constraint.MarkupType.EXACT
                it < exact + inclu -> Constraint.MarkupType.INCLUDED
                else -> Constraint.MarkupType.NO
            }
        }.zip(constraintPipViews).forEach { (markUp, pipView) ->
            // dimensions
            if (!appearance.hasStableDimensions) {
                pipView.updateLayoutParams {
                    val size = appearance.getPipSize(guess, markUp).toInt()
                    width = size
                    height = size
                    if (this is MarginLayoutParams) {
                        setMargins(appearance.getPipMargin(guess, markUp).toInt())
                    }
                }
            }

            // colors / details
            if (pipView is MaterialCardView) {
                pipView.radius = appearance.getPipCornerRadius(guess, markUp)
                pipView.setCardBackgroundColor(appearance.getColorFill(guess, markUp, swatch))
                pipView.strokeColor = appearance.getColorStroke(guess, markUp, swatch)
                pipView.strokeWidth = appearance.getPipStrokeWidth(guess, markUp)
                pipView.elevation = appearance.getPipElevation(guess, markUp)
            } else {
                pipView.setBackgroundColor(appearance.getColorFill(guess, markUp, swatch))
                pipView.elevation = appearance.getPipElevation(guess, markUp)
            }
        }

        noPipImageView.setColorFilter(swatch.evaluation.noVariant, PorterDuff.Mode.MULTIPLY)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}