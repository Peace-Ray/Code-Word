package com.peaceray.codeword.presentation.view.component.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.view.component.viewholders.GuessViewHolder
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import timber.log.Timber
import javax.inject.Inject

/**
 * A [RecyclerView.Adapter] displaying Constraints and a pending guess
 * (as Guess objects, the View DataModel wrapper for this concept). Each item in the RecyclerView
 * represents one Guess; this adapter is appropriate for list-style layouts.
 *
 * Default behavior is to display the Guess, if any, as the final item on the list.
 */
class GuessRowAdapter @Inject constructor(
    private val layoutInflater: LayoutInflater,
    private val colorSwatchManager: ColorSwatchManager
): GuessAdapter<GuessViewHolder>() {

    //region View configuration
    //---------------------------------------------------------------------------------------------
    @LayoutRes var layoutId: Int = R.layout.cell_guess

    constructor(
        layoutInflater: LayoutInflater,
        colorSwatchManager: ColorSwatchManager,
        @LayoutRes layoutId: Int
    ): this(layoutInflater, colorSwatchManager) {
        this.layoutId = layoutId
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuessViewHolder {
        Timber.v("onCreateViewHolder: $viewType")
        val cellView = layoutInflater.inflate(layoutId, parent, false)
        return GuessViewHolder(cellView, layoutInflater, colorSwatchManager)
    }

    override fun onBindGuessToViewHolder(holder: GuessViewHolder, position: Int, guess: Guess) {
        // animation rules:
        // DO animate replacement of guess with the same constraint at the same position
        // (don't animate anything else for now)
        // TODO other animation cases?
        val animate = holder.adapterPosition == position
                && holder.guess.isGuess && guess.isEvaluation
                && holder.guess.candidate == guess.candidate
        holder.bind(guess, animate)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    override fun guessRangeToItemRange(guessStart: Int, guessCount: Int) = Pair(guessStart, guessCount)
    override fun guessSliceToItemRange(guessPosition: Int, sliceStart: Int, sliceCount: Int) =
        Pair(guessPosition, 1)
    override fun itemRangeToGuessRange(itemStart: Int, itemCount: Int) = Pair(itemStart, itemCount)
    //---------------------------------------------------------------------------------------------
    //endregion

}