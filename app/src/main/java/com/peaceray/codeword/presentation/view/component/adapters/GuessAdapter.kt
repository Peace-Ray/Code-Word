package com.peaceray.codeword.presentation.view.component.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.view.component.viewholders.GuessViewHolder
import com.peaceray.codeword.presentation.datamodel.Guess
import timber.log.Timber
import javax.inject.Inject

/**
 * A [RecyclerView.Adapter] displaying Constraints and a pending guess
 * (as Guess objects, the View DataModel wrapper for this concept).
 *
 * Default behavior is to display the Guess, if any, as the final item on the list.
 */
class GuessAdapter @Inject constructor(
    val layoutInflater: LayoutInflater
): RecyclerView.Adapter<GuessViewHolder>() {

    //region View configuration
    //---------------------------------------------------------------------------------------------
    @LayoutRes var layoutId: Int = R.layout.cell_guess

    constructor(layoutInflater: LayoutInflater, @LayoutRes layoutId: Int): this(layoutInflater) {
        this.layoutId = layoutId
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuessViewHolder {
        Timber.v("onCreateViewHolder: $viewType")
        val cellView = layoutInflater.inflate(layoutId, parent, false)
        return GuessViewHolder(cellView, layoutInflater)
    }

    override fun onBindViewHolder(holder: GuessViewHolder, position: Int) {
        Timber.v("onBindViewHolder: $position")

        val bindData = if (position < constraints.size) {
            constraints[position]
        } else if (position == constraints.size && guess != null) {
            guess!!
        } else {
            placeholder
        }

        // animation rules:
        // DO animate replacement of guess with the same constraint at the same position
        // (don't animate anything else for now)
        // TODO other animation cases?
        val animate = holder.adapterPosition == position
                && holder.guess.isGuess && bindData.isEvaluation
                && holder.guess.candidate == bindData.candidate
        holder.bind(bindData, animate)
    }

    override fun getItemCount(): Int {
        var wordRows = constraints.size
        if (guess != null) wordRows++
        val items = Math.max(wordRows, rows)

        Timber.v("getItemCount: $items")
        return items
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Data Model
    //---------------------------------------------------------------------------------------------
    private var length: Int = 0
        private set

    private var rows: Int = 0
        private set

    var placeholder = Guess.placeholder
        private set

    var constraints = mutableListOf<Guess>()
        private set

    var guess: Guess? = null
        private set

    /**
     * Set the size of the displayed game field. If [rows] is > 0, the displayed rows will be
     * padded to that size. Otherwise, the space required for all constraints and non-null Guess
     * will be used.
     *
     * @param length The word length
     * @param rows Always-visible rows. Useful for fixed-length games. Length will be padded
     * up to this amount with empty guess fields.
     */
    fun setGameFieldSize(length: Int, rows: Int) {
        if (this.length != length || this.rows != rows) {
            if (this.length != length) {
                constraints.clear()
                placeholder = Guess(length)
                guess = if (guess != null) Guess(length, guess!!.candidate) else null
            }


            this.length = length
            this.rows = rows

            notifyDataSetChanged()
        }
    }

    /**
     * Clear all constraints and guesses.
     */
    fun clear() {
        this.constraints.clear()
        this.guess = null

        notifyDataSetChanged()
    }

    /**
     * Set existing constraints, maintaining the guess (if any). The nuclear option; if possible,
     * use [replaceGuess] to append a constraint and/or replace a guess for smoother animation.
     *
     * Has no effect on current [guess].
     *
     * @param constraints The new constraints to set.
     */
    fun setConstraints(constraints: List<Constraint>) {
        this.constraints.clear()
        this.constraints.addAll(constraints.map { Guess(it) })

        // TODO Look for changes (identify insertions, appends, etc. within and outside padding)
        notifyDataSetChanged()
    }

    /**
     * Sets existing constraints and guess. The nuclear option; if possible, use [replaceGuess]
     * to append a constraint and/or replace a guess for smoother animation.
     *
     * @param constraints The new constraints to set.
     * @param guess The new guess to set.
     */
    fun setConstraintsAndGuess(constraints: List<Constraint>, guess: String?) {
        this.constraints.clear()
        this.constraints.addAll(constraints.map { Guess(it) })
        this.guess = if (guess != null) Guess(length, guess) else null

        // TODO Look for changes (identify insertions, appends, etc. within and outside padding)
        notifyDataSetChanged()
    }

    /**
     * Replace the existing guess with a new guess, a constraint, or both. Replacements are smooth.
     * Where possible, this is the preferred update function.
     *
     * @param guess If provided, text of the new guess. Default: null (i.e.: setting a "no guess" state).
     * @param constraint If provided, a new constraint to append to existing ones. Default: null
     * (i.e. "do not add a new constraint").
     */
    fun replaceGuess(guess: String? = null, constraint: Constraint? = null) {
        if (constraint != null) {
            // the new constraint replaces the existing guess or placeholder.
            constraints.add(Guess(constraint))
            if (constraints.size <= length || this.guess != null) {
                notifyItemChanged(constraints.size - 1)
            } else {
                notifyItemInserted(constraints.size - 1)
            }

            // the new guess, if any, represents a placeholder change or addition.
            // This call will never insert a constraint that shifts an active guess,
            // even if the old and new guesses have the same text.
            this.guess = if (guess == null) null else Guess(length, guess)
            if (this.guess != null) {
                if (constraints.size < length) {
                    notifyItemChanged(constraints.size)
                } else {
                    notifyItemInserted(constraints.size)
                }
            }
        } else {
            // could alter the guess, insert one, or remove the existing one.
            val oldGuess = this.guess
            this.guess = if (guess == null) null else Guess(length, guess)

            // notify this specific position. Guesses are displayed after all constraints.
            if ((oldGuess != null && guess != null) || constraints.size < rows) {
                // same position; content change
                Timber.v("changed characters at row ${constraints.size}")
                notifyItemChanged(constraints.size)
            } else if (oldGuess != null) {
                // removed the guess
                notifyItemRemoved(constraints.size)
            } else if (guess != null) {
                // added a guess
                notifyItemInserted(constraints.size)
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}