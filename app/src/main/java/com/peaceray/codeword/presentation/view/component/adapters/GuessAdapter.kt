package com.peaceray.codeword.presentation.view.component.adapters

import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.Guess
import timber.log.Timber

abstract class GuessAdapter<T: RecyclerView.ViewHolder>: RecyclerView.Adapter<T>() {

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onBindViewHolder(holder: T, position: Int) {
        val guessRange = itemRangeToGuessRange(position, 1)
        val guessPosition = guessRange.first
        val bindGuess = if (guessPosition < constraints.size) {
            constraints[guessPosition]
        } else if (guessPosition == constraints.size && guess != null) {
            guess!!
        } else {
            placeholder
        }

        onBindGuessToViewHolder(holder, position, bindGuess)
    }

    abstract fun onBindGuessToViewHolder(holder: T, position: Int, guess: Guess)

    override fun getItemCount(): Int {
        var wordRows = constraints.size
        if (guess != null) wordRows++
        val items = Math.max(wordRows, rows)
        val (start, count) = guessRangeToItemRange(0, items)
        return start + count
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data View Access
    //---------------------------------------------------------------------------------------------
    fun guessItemRange(placeholders: Boolean = false): IntRange {
        val length = if (placeholders) length else guess?.candidate?.length ?: 0
        return if (length == 0) IntRange.EMPTY else {
            val itemRange = guessSliceToItemRange(constraints.size, 0, length)
            IntRange(itemRange.first, itemRange.first + itemRange.second - 1)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Model
    //---------------------------------------------------------------------------------------------
    var length: Int = 0
        private set

    var rows: Int = 0
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
                notifyGuessChanged(constraints.size - 1, this.guess, constraints.last())
            } else {
                val range = guessRangeToItemRange(constraints.size - 1, 1)
                notifyItemRangeInserted(range.first, range.second)
            }

            // the new guess, if any, represents a placeholder change or addition.
            // This call will never insert a constraint that shifts an active guess,
            // even if the old and new guesses have the same text.
            this.guess = if (guess == null) null else Guess(length, guess)
            if (this.guess != null) {
                if (constraints.size < rows) {
                    notifyGuessChangedFromPlaceholder(constraints.size, this.guess)
                } else {
                    val range = guessRangeToItemRange(constraints.size, 1)
                    notifyItemRangeInserted(range.first, range.second)
                }
            }
        } else {
            // could alter the guess, insert one, or remove the existing one.
            val oldGuess = this.guess
            val oldBinding = this.guess ?: placeholder
            this.guess = if (guess == null) null else Guess(length, guess)

            // notify this specific position. Guesses are displayed after all constraints.
            if ((oldGuess != null && guess != null) || constraints.size < rows) {
                // same position; content change
                notifyGuessChanged(constraints.size, oldBinding, this.guess)
            } else if (oldGuess != null) {
                // removed the guess
                val range = guessRangeToItemRange(constraints.size, 1)
                notifyItemRangeRemoved(range.first, range.second)
            } else if (guess != null) {
                // added a guess
                val range = guessRangeToItemRange(constraints.size, 1)
                notifyItemRangeInserted(range.first, range.second)
            }
        }
    }

    private fun notifyGuessChangedFromPlaceholder(guessPosition: Int, guess: Guess?) {
        if (guess != null) {
            val changeStart = guess.lettersPadded.indexOfFirst { !it.isPlaceholder }
            val changeEnd = guess.lettersPadded.indexOfLast { !it.isPlaceholder }

            if (changeStart >= 0) {
                val range = guessSliceToItemRange(
                    guessPosition,
                    changeStart,
                    changeEnd - changeStart + 1
                )
                notifyItemRangeChanged(range.first, range.second)
            }
        }
    }

    private fun notifyGuessChanged(guessPosition: Int, oldGuess: Guess?, newGuess: Guess?) {
        if (oldGuess == null || newGuess == null) {
            notifyGuessChangedFromPlaceholder(guessPosition, oldGuess ?: newGuess)
        } else {
            val zipped = oldGuess.lettersPadded.zip(newGuess.lettersPadded)
            val changeStart = zipped.indexOfFirst { (old, new) -> old != new }

            val range: Pair<Int, Int> = if (changeStart < 0) guessRangeToItemRange(guessPosition, 1) else {
                val changeEnd = zipped.indexOfLast { (old, new) -> old != new }
                guessSliceToItemRange(guessPosition, changeStart, changeEnd - changeStart + 1)
            }

            notifyItemRangeChanged(range.first, range.second)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    /**
     * Convert a range of guesses (position and count) into a range of view items.
     * For Adapters where each Guess is represented by a single ViewHolder, this in identity.
     * Other Adapters may use other representations, such as one ViewHolder per letter.
     */
    abstract fun guessRangeToItemRange(guessStart: Int, guessCount: Int): Pair<Int, Int>

    /**
     * Convert a guess substring into a range of view items. For adapters where each Guess is represented
     * by a single ViewHolder, this is Pair(guessPosition, 1). Other adapters may use other
     * representations, such as one ViewHolder per letter.
     */
    abstract fun guessSliceToItemRange(guessPosition: Int, sliceStart: Int, sliceCount: Int): Pair<Int, Int>

    /**
     * Convert a range of view items (position and count) to a range of guesses.
     * For Adapters where each Guess is represented by a single ViewHolder, this in identity.
     * Other Adapters may use other representations, such as one ViewHolder per letter.
     */
    abstract fun itemRangeToGuessRange(itemStart: Int, itemCount: Int): Pair<Int, Int>
    //---------------------------------------------------------------------------------------------
    //endregion

}