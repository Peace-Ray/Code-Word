package com.peaceray.codeword.presentation.view.component.adapters.guess

import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.Guess
import timber.log.Timber

abstract class BaseGuessAdapter<T: RecyclerView.ViewHolder>: GuessAdapter, RecyclerView.Adapter<T>() {

    //region RecyclerView.Adapter Implementation
    //---------------------------------------------------------------------------------------------
    override fun onBindViewHolder(holder: T, position: Int) {
        val guessRange = itemRangeToGuessRange(position, 1)
        val guessPosition = guessRange.first
        val bindGuess = when {
            guessPosition < constraints.size -> constraints[guessPosition]
            guessPosition - constraints.size < _guesses.size -> _guesses[guessPosition - constraints.size]
            else -> placeholder
        }

        onBindGuessToViewHolder(holder, position, bindGuess)
    }

    abstract fun onBindGuessToViewHolder(holder: T, position: Int, guess: Guess)

    override fun getItemCount(): Int {
        val wordRows = constraints.size + _guesses.size
        val items = Math.max(wordRows, rows)
        val (start, count) = guessRangeToItemRange(0, items)
        return start + count
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Model
    //---------------------------------------------------------------------------------------------
    var length: Int = 0
        private set

    var rows: Int = 0
        private set

    private val _constraints = mutableListOf<Guess>()
    val constraints: List<Guess>
        get() = _constraints
    
    private val _guesses = mutableListOf<Guess>()
    val guesses: List<Guess>
        get() = _guesses

    var placeholder = Guess.placeholder
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
    override fun setGameFieldSize(length: Int, rows: Int) {
        if (this.length != length || this.rows != rows) {
            if (this.length != length) {
                _constraints.clear()
                placeholder = Guess(length)

                for (i in 0 until _guesses.size) {
                    _guesses[i] = Guess(length, _guesses[i].candidate)
                }
            }

            this.length = length
            this.rows = rows

            notifyDataSetChanged()
        }
    }

    /**
     * Clear all constraints and guesses.
     */
    override fun clear() {
        _constraints.clear()
        _guesses.clear()

        notifyDataSetChanged()
    }

    /**
     * Sets existing constraints and guesses. The nuclear option; if possible, use [update]
     * to append a constraint and/or replace a guess for smoother animation.
     *
     * @param constraints The new constraints to set. If null, no changes to constraints will be
     * made; if non-null (even if empty) the Constraints provided will fully replace those previously
     * there.
     * @param guesses The new guesses to set. If null, no changes to guesses will be
     * made; if non-null (even if empty) the guesses provided will fully replace those previously
     * there.
     */
    override fun replace(constraints: List<Constraint>?, guesses: List<String>?) {
        if (constraints != null) updateConstraints(true, constraints)
        if (guesses != null) updateGuesses(true, guesses)
    }

    /**
     * Appends new constraint(s) and/or guess(es), smoothly updating display and maintaining other
     * the constraints and guesses already present.
     *
     * Note: will not replace an ongoing guess with the resulting constraint or new guess(es). Any
     * existing guesses will remain, between the newly added constraints and newly added guesses.
     *
     * @param constraints The new constraints to add. Will be appended to any existing constraints,
     * between them and any guesses. If null or empty no changes will be made to constraints.
     * @param guesses The new guesses to add. Will be appended to any existing guesses. If null
     * or empty no changes will be made to guesses.
     */
    override fun append(constraints: List<Constraint>?, guesses: List<String>?) {
        if (constraints != null) updateConstraints(false, constraints)
        if (guesses != null) updateGuesses(false, guesses)
    }

    private fun updateConstraints(replace: Boolean, constraints: List<Constraint>) {
        Timber.v("updateConstraints")
        val removed = if (_constraints.isEmpty() || !replace) null else {
            val range = guessRangeToItemRange(0, _constraints.size)
            _constraints.clear()
            range
        }

        val added = if (constraints.isEmpty()) null else {
            _constraints.addAll(constraints.map { Guess(it) })
            guessRangeToItemRange(_constraints.size - constraints.size, constraints.size)
        }

        notifyRangeUpdates(removed = removed, added = added)
    }

    private fun updateGuesses(replace: Boolean, guesses: List<String>) {
        Timber.v("updateGuesses")
        val removed = if (_guesses.isEmpty() || !replace) null else {
            val range = guessRangeToItemRange(_constraints.size, _guesses.size)
            _guesses.clear()
            range
        }

        val added = if (guesses.isEmpty()) null else {
            _guesses.addAll(guesses.map { Guess(length, it) })
            guessRangeToItemRange(_constraints.size + _guesses.size - guesses.size, guesses.size)
        }

        notifyRangeUpdates(removed = removed, added = added)
    }

    private fun notifyRangeUpdates(removed: Pair<Int, Int>? = null, added: Pair<Int, Int>? = null) {
        // TODO animate insertions / removals, taking care not to disrupt the placeholder row counts
        if (removed != null || added != null) {
            notifyDataSetChanged()
        }
    }


    /**
     * Replace the existing guess with a new guess, a constraint, or both. Replacements are smooth.
     * Where possible, this is the preferred update function.
     *
     * @param constraint If provided, a new constraint to append to existing ones. Default: null
     * (i.e. "do not add a new constraint"). If the adapter held any guesses, a non-null Constraint
     * will replace the first such guess (first-in first-out).
     * @param guess If provided, text of the new guess. Default: null (i.e.: setting a "no guess" state).
     * If any guess(es) are already in place, the last such guess will be replaced. Otherwise, a new
     * guess will be appended to any existing constraints.
     *
     */
    override fun update(constraint: Constraint?, guess: String?) {
        Timber.v("update constraint = $constraint, guess = $guess")
        if (constraint != null) {
            // the new constraint replaces the first available guess or placeholder.
            _constraints.add(Guess(constraint))
            val removed = _guesses.removeFirstOrNull()

            // notify
            if (removed != null || _constraints.size <= rows) {
                notifyGuessChanged(_constraints.size - 1, removed, _constraints.last())
            } else {
                val range = guessRangeToItemRange(_constraints.size - 1, 1)
                notifyItemRangeInserted(range.first, range.second)
            }
        }

        if (guess != null) {
            // could alter the guess, insert one, or remove the existing one.
            val oldGuess = _guesses.removeLastOrNull()
            val oldBinding = oldGuess ?: placeholder
            val newBinding = Guess(length, guess)
            _guesses.add(newBinding)

            if ((oldGuess != null) || _constraints.size + _guesses.size <= rows) {
                // same position; content change
                notifyGuessChanged(_constraints.size + _guesses.size - 1, oldBinding, newBinding)
            } else {
                // added a guess
                val range = guessRangeToItemRange(_constraints.size + _guesses.size - 1, 1)
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
            val changeStart = zipped.indexOfFirst { (old, new) -> !old.isSameAs(new) }

            val range: Pair<Int, Int> = if (changeStart < 0) guessRangeToItemRange(guessPosition, 0) else {
                val changeEnd = zipped.indexOfLast { (old, new) -> !old.isSameAs(new) }
                guessSliceToItemRange(guessPosition, changeStart, changeEnd - changeStart + 1)
            }

            notifyItemRangeChanged(range.first, range.second)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    override fun activeItemRange(placeholders: Boolean): Pair<Int, Int> {
        val firstGuess = _constraints.size
        val lastGuess = _constraints.size + _guesses.size - 1
        val length = if (placeholders) length else _guesses.lastOrNull()?.candidate?.length ?: 0
        
        return if (firstGuess == lastGuess) { 
            guessSliceToItemRange(firstGuess, 0, length)
        } else {
            val rangeTo = guessRangeToItemRange(firstGuess, lastGuess - firstGuess)
            val range = guessSliceToItemRange(lastGuess, 0, length)
            Pair(rangeTo.first, (range.first - rangeTo.first) + range.second)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}