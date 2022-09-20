package com.peaceray.codeword.presentation.datamodel

import com.peaceray.codeword.game.data.Constraint

/**
 * A View-specific wrapper for the base game model's [Constraint] class, representing a candidate
 * guess. Along with nullable [constraint], includes nonzero [length] (expected guess length) and
 * nonnull [candidate].
 *
 * @property length The expected length for a guess. May be 0 for non-null placeholder values, but
 * never for gameplay.
 * @property candidate The candidate string (may have length [0..length])
 * @property constraint The constraint / markup applied to the guess (if any)
 * @property isPlaceholder Is this ViewDataGuess a 0-length placeholder?
 * @property isEmpty Is this ViewDataGuess an empty field where a guess may go?
 * @property isGuess Is this ViewDataGuess a fully-entered (but not evaluated) guess?
 * @property isEvaluation Is this ViewDataGuess an evaluation (i.e. does it have a [constraint])?
 * @property letters A List of [GuessLetter]s including any evaluation markup. Limited to
 * the length of the [candidate].
 * @property lettersPadded A list of [GuessLetter]s, padding to [length] if necessary.
 * Padding letters will be the space character ' '.
 */
data class Guess private constructor(val length: Int, val candidate: String, val constraint: Constraint?) {
    constructor(length: Int): this(length, "", null)

    constructor(length: Int, candidate: String): this(length, candidate, null)

    constructor(constraint: Constraint): this(constraint.candidate.length, constraint.candidate, constraint)

    val isPlaceholder = length == 0
    val isEmpty = length > 0 && constraint == null && candidate.isEmpty()
    val isGuess = length > 0 && length == candidate.length && constraint == null
    val isEvaluation = constraint != null

    val letters by lazy { List(if (length < candidate.length) length else candidate.length) {
        GuessLetter(it, this, candidate[it], constraint?.markup?.get(it))
    } }

    val lettersPadded by lazy { List(length) {
        val letter = if (it < candidate.length) candidate[it] else ' '
        val markup = constraint?.markup?.get(it)
        GuessLetter(it, this, letter, markup)
    } }

    companion object {
        val placeholder = Guess(0)
    }
}

/**
 * A View-specific wrapper for a single character of a guess, possibly with markup. A GuessLetter
 * includes markup and in-guess positioning, along with the Guess containing it. Comparison
 * between instances should prefer `isSame...` functions since (e.g.) the equality comparator ==
 * will check that the containing Guess is identical, and will be false even if this letter and
 * its markup match.
 *
 * @property character The guess character.
 * @property markup The markup; may be empty.
 */
data class GuessLetter(val position: Int, val guess: Guess, val character: Char = ' ', val markup: Constraint.MarkupType? = null) {
    /**
     * Convenience constructor for stand-alone guess letters, not connected to a larger guess,
     * e.g. when displaying a legend for markup UI.
     */
    constructor(character: Char, markup: Constraint.MarkupType? = null): this(
        0,
        Guess(1, "$character"),
        character,
        markup
    )

    val isPlaceholder = character == ' '
    val isGuess = character != ' ' && markup == null
    val isEvaluation = markup != null

    companion object {
        val placeholder = GuessLetter(-1, Guess.placeholder)
        fun placeholders(length: Int) = Guess(length).lettersPadded
    }

    /**
     * A comparator: checks if this [GuessLetter] is the same as another in character,
     * position, and markup. Intended for in-place comparison of a partial or complete guess
     * that has not yet been evaluated.
     *
     * Ignores the equality of the underlying guess value, so that
     * e.g. the letter "I" in "PI___", "PIO__", "PIOU_", and "PIOUS" will pass this check until
     * evaluation is added, at which point the comparator will return [false].
     */
    fun isSameAs(guessLetter: GuessLetter) = (
            position == guessLetter.position &&
                    character == guessLetter.character &&
                    markup == guessLetter.markup
    )

    /**
     * A comparator: checks if this [GuessLetter] is the same as another in character and position.
     * Intended for in-place comparison of a partial or complete guess before or after evaluation.
     *
     * Ignores the equality of the underlying guess value and any markup, so that
     * e.g. the letter "I" in "PI___", "PIO__", "PIOU_", and "PIOUS" will pass this check up to
     * and including when evaluation has been assigned as markup.
     */
    fun isSameCandidateAs(guessLetter: GuessLetter) = (
            position == guessLetter.position &&
                    character == guessLetter.character
    )
}