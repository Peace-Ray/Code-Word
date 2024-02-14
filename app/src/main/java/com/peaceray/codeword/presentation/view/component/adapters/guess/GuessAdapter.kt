package com.peaceray.codeword.presentation.view.component.adapters.guess

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.datamodel.guess.Guess

/**
 * An Adapter that holds Constraints and Guesses (in most scenarios at most 1 guess will be present
 * at a time, but some use cases -- e.g. splash screens and demos -- might queue multiple guesses).
 */
interface GuessAdapter {

    //region Data Binding
    //---------------------------------------------------------------------------------------------
    /**
     * Set the size of the displayed game field. If [rows] is > 0, the displayed rows will be
     * padded to that size. Otherwise, the space required for all constraints and non-null Guess
     * will be used.
     *
     * @param length The word length
     * @param rows Always-visible rows. Useful for fixed-length games. Length will be padded
     * up to this amount with empty guess fields.
     */
    fun setGameFieldSize(length: Int, rows: Int)

    /**
     * Clear all constraints and guesses.
     */
    fun clear()

    /**
     * Sets existing constraints and guesses. The nuclear option; if possible, use [advance]
     * to append a constraint and/or replace a guess for smoother animation.
     *
     * @param constraints The new constraints to set. If null, no changes to constraints will be
     * made; if non-null (even if empty) the Constraints provided will fully replace those previously
     * there.
     * @param guesses The new guesses to set. If null, no changes to guesses will be
     * made; if non-null (even if empty) the guesses provided will fully replace those previously
     * there.
     */
    fun replace(constraints: List<Guess>? = null, guesses: List<Guess>? = null)

    /**
     * Appends new constraint(s) and/or guess(es), smoothly updating display and maintaining other
     * the constraints and guesses already present. For updates that occur during gameplay,
     * [advance] is preferred.
     *
     * Note: will not replace an ongoing guess with the resulting constraint or new guess(es). Any
     * existing guesses will remain, between the newly added constraints and newly added guesses.
     * 
     * @param constraints The new constraints to add. Will be appended to any existing constraints,
     * between them and any guesses. If null or empty no changes will be made to constraints.
     * @param guesses The new guesses to add. Will be appended to any existing guesses. If null
     * or empty no changes will be made to guesses.
     */
    fun append(constraints: List<Guess>? = null, guesses: List<Guess>? = null)

    /**
     * Replace an existing guess with a new guess, a constraint, or both. Replacements are smooth.
     * Where possible, this is the preferred update function. Games typically allow up to one
     * active guess, so both updates will be performed on the "active" guess slot.
     *
     * @param constraint If provided, a new constraint to append to existing ones. Default: null
     * (i.e. "do not add a new constraint"). If the adapter held any guesses, a non-null Constraint
     * will replace the first such guess (first-in first-out).
     * @param guess If provided, text of the new guess. Default: null (i.e.: setting a "no guess" state).
     * If any guess(es) are already in place, the last such guess will be replaced. Otherwise, a new
     * guess will be appended to any existing constraints.
     *
     */
    fun advance(constraint: Guess? = null, guess: Guess? = null)
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Item Ranges
    //---------------------------------------------------------------------------------------------
    val itemsPerGameRow: Int

    /**
     * Suggests a "span" for the item at the given position. Should consistently represent
     * relative sizes to ensure that content is appropriate displayed row-by-row. Since the total
     * number of columns used for display is not known to the Adapter, this is a hint only.
     */
    fun positionSpan(position: Int): Int

    /**
     * Return a range of all "active" items: items that can be altered by a call to [advance].
     *
     * @param placeholders Include placeholder blocks in the range returned (typically at the
     * end of the current guess row).
     */
    fun activeItemRange(placeholders: Boolean = false): Pair<Int, Int>

    /**
     * Convert a range of constraints (position and count) into a range of view items.
     * For Adapters where each Constraint is represented by a single ViewHolder, this in identity.
     * Other Adapters may use other representations, such as one ViewHolder per letter.
     */
    fun guessRangeToItemRange(constraintStart: Int, constraintCount: Int): Pair<Int, Int>

    /**
     * Convert a guess substring into a range of view items. For adapters where each Guess is represented
     * by a single ViewHolder, this is Pair(guessPosition, 1). Other adapters may use other
     * representations, such as one ViewHolder per letter.
     */
    fun guessSliceToItemRange(guessPosition: Int, sliceStart: Int, sliceCount: Int): Pair<Int, Int>

    /**
     * Convert a range of view items (position and count) to a range of constraints.
     * For Adapters where each Constraint is represented by a single ViewHolder, this in identity.
     * Other Adapters may use other representations, such as one ViewHolder per letter.
     */
    fun itemRangeToGuessRange(itemStart: Int, itemCount: Int): Pair<Int, Int>
    //---------------------------------------------------------------------------------------------
    //endregion

}