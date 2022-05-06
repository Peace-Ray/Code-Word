package com.peaceray.codeword.presentation.view.component.viewholders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.core.view.children
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager

class GuessViewHolder(
    itemView: View,
    val layoutInflater: LayoutInflater,
    val colorSwatchManager: ColorSwatchManager
): RecyclerView.ViewHolder(itemView) {

    //region View
    //---------------------------------------------------------------------------------------------
    @LayoutRes var layoutId: Int = R.layout.cell_letter

    private val guessLetterContainer: ViewGroup = itemView.findViewById(R.id.guessLetterContainer)
    private val guessLetterTextViews = mutableListOf<TextView>()

    init {
        guessLetterContainer.children.forEachIndexed { index, view ->
            val v = view.findViewById<TextView>(R.id.textView)
            if (v != null) guessLetterTextViews.add(v)
        }

        val context = itemView.context
        if (context is LifecycleOwner) {
            colorSwatchManager.colorSwatchLiveData.observe(context) { swatch ->
                setGuessColors(swatch, false)
            }
        }
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
        var colorChange = _guess.constraint != guess.constraint
        _guess = guess

        // extend letter views
        while (guessLetterContainer.childCount < guess.length) {
            val view = layoutInflater.inflate(layoutId, guessLetterContainer, true)
            guessLetterTextViews.add(view.findViewById(R.id.textView))
            colorChange = true
        }

        // adjust visibility
        guessLetterContainer.children.forEachIndexed { index, view ->
            // TODO animate change. Grow / shrink / flip?
            view.visibility = if (index < guess.length) View.VISIBLE else View.GONE
            colorChange = true
        }

        // set content
        guess.lettersPadded.forEachIndexed { index, guessLetter ->
            val text = "${guessLetter.character}"
            if (guessLetterTextViews[index].text != text) {
                guessLetterTextViews[index].text = text
            }
            // TODO animate any change
        }
        
        if (colorChange) {
            setGuessColors(colorSwatchManager.colorSwatch, animate)
        }
    }

    private fun setGuessColors(swatch: ColorSwatch, animate: Boolean) {
        guess.lettersPadded.zip(guessLetterTextViews).forEach { (letter, textView) ->
            textView.setBackgroundColor(swatch.evaluation.color(letter.markup))
            textView.setTextColor(swatch.evaluation.onColor(letter.markup))
            // TODO animate any change
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}