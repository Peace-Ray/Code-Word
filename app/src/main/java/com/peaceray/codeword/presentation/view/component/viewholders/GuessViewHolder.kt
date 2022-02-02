package com.peaceray.codeword.presentation.view.component.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.Guess
import timber.log.Timber

class GuessViewHolder(val itemView: View, val layoutInflater: LayoutInflater): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    @LayoutRes
    var layoutId: Int = R.layout.cell_letter

    private val guessLetterContainer: ViewGroup = itemView.findViewById(R.id.guessLetterContainer)
    private val guessLetterTextViews = mutableListOf<TextView>()

    init {
        guessLetterContainer.children.forEachIndexed { index, view ->
            val v = view.findViewById<TextView>(R.id.textView)
            if (v != null) guessLetterTextViews.add(v)
        }

        Timber.v("Has ${guessLetterTextViews.size} TextViews")
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data
    //---------------------------------------------------------------------------------------------
    private var _guess = Guess.placeholder
    var guess
        get() = _guess
        set(value) = bind(value)

    fun bind(guess: Guess, animate: Boolean = false) {
        // extend letter views
        while (guessLetterContainer.childCount < guess.length) {
            val view = layoutInflater.inflate(layoutId, guessLetterContainer, true)
            guessLetterTextViews.add(view.findViewById(R.id.textView))
        }

        // adjust visibility
        guessLetterContainer.children.forEachIndexed { index, view ->
            // TODO animate change. Grow / shrink / flip?
            view.visibility = if (index < guess.length) View.VISIBLE else View.GONE
        }

        // set content
        guess.lettersPadded.forEachIndexed { index, guessLetter ->
            // TODO apply custom color scheme
            guessLetterTextViews[index].text = "${guessLetter.character}"
            guessLetterTextViews[index].setBackgroundColor(when(guessLetter.markup) {
                Constraint.MarkupType.EXACT -> itemView.resources.getColor(R.color.green_500)
                Constraint.MarkupType.INCLUDED -> itemView.resources.getColor(R.color.amber_300)
                Constraint.MarkupType.NO -> itemView.resources.getColor(R.color.gray_500)
                else -> itemView.resources.getColor(R.color.design_default_color_surface)
            })
            guessLetterTextViews[index].setTextColor(if (guessLetter.markup == null) {
                itemView.resources.getColor(R.color.gray_500)
            } else {
                itemView.resources.getColor(R.color.white)
            })

            // TODO animate any change
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}