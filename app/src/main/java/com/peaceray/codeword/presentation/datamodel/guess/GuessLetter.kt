package com.peaceray.codeword.presentation.datamodel.guess

data class GuessLetter(
    val position: Int,
    val character: Char = SPACE,
    val markup: GuessMarkup = GuessMarkup.EMPTY,
    val candidates: Set<Char>? = null,
    val type: GuessType = when {
        markup != GuessMarkup.EMPTY -> GuessType.EVALUATION
        character != SPACE -> GuessType.GUESS
        else -> GuessType.PLACEHOLDER
    }
) {

    init {
        if ((character == SPACE) != (type == GuessType.PLACEHOLDER)) {
            throw IllegalStateException("PLACEHOLDER letters must have SPACE character")
        }
        if (markup != GuessMarkup.EMPTY && markup != GuessMarkup.ALLOWED && type != GuessType.EVALUATION) {
            throw IllegalStateException("Letters with markup must have EVALUATION type")
        }
    }

    private companion object {
        const val SPACE = ' '
    }

    val isPlaceholder = type == GuessType.PLACEHOLDER
    val isGuess = type == GuessType.GUESS
    val isEvaluation = type == GuessType.EVALUATION

    /**
     * A comparator: checks if this [GuessLetter] is the same as another in character,
     * position, and markup. Intended for in-place comparison of a partial or complete guess
     * that has not yet been evaluated.
     *
     * Ignores the equality of the underlying guess value, so that
     * e.g. the letter "I" in "PI___", "PIO__", "PIOU_", and "PIOUS" will pass this check until
     * evaluation is added, at which point the comparator will return false.
     */
    fun isSameAs(guessLetter: GuessLetter) = (
            position == guessLetter.position &&
                    character == guessLetter.character &&
                    markup == guessLetter.markup &&
                    type == guessLetter.type
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