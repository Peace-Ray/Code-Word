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
import com.peaceray.codeword.presentation.view.component.viewholders.GuessLetterViewHolder
import timber.log.Timber
import javax.inject.Inject

/**
 * A [RecyclerView.Adapter] displaying Constraints and a pending guess
 * (as Guess objects, the View DataModel wrapper for this concept). Each item in the RecyclerView
 * represents on [GuessLetter] in the Guess; this Adapter is appropriate for Grid-style layouts.
 *
 * Default behavior is to display the Guess, if any, as the final item on the list.
 */
class GuessLetterAdapter @Inject constructor(
    private val layoutInflater: LayoutInflater,
    private val colorSwatchManager: ColorSwatchManager
): GuessAdapter<GuessLetterViewHolder>() {

    //region View configuration
    //---------------------------------------------------------------------------------------------
    @LayoutRes var layoutId: Int = R.layout.cell_letter

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
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuessLetterViewHolder {
        Timber.v("onCreateViewHolder: $viewType")
        val cellView = layoutInflater.inflate(layoutId, parent, false)
        return GuessLetterViewHolder(cellView, layoutInflater, colorSwatchManager)
    }

    override fun onBindGuessToViewHolder(
        holder: GuessLetterViewHolder,
        position: Int,
        guess: Guess
    ) {
        val bindLetter = guess.lettersPadded[position % length]
        holder.bind(bindLetter)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    override fun guessRangeToItemRange(guessStart: Int, guessCount: Int) = Pair(
        guessStart * length,
        guessCount * length
    )

    override fun guessSliceToItemRange(guessPosition: Int, sliceStart: Int, sliceCount: Int) = Pair(
        guessPosition * length + sliceStart,
        sliceCount
    )

    override fun itemRangeToGuessRange(itemStart: Int, itemCount: Int): Pair<Int, Int> {
        val guessStart = itemStart / length
        val guessEnd = (itemStart + itemCount - 1) / length
        return Pair(guessStart, guessEnd - guessStart + 1)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}