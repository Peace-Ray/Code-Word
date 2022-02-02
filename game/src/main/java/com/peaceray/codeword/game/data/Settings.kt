package com.peaceray.codeword.game.data

/**
 * Settings for how the CodeGame operates.
 *
 * @param letters Code width (characters)
 * @param rounds Game length (maximum number of attempts)
 * @param constraintPolicy How constraints revealed by previous guesses are treated when validating
 * a new guess.
 */
data class Settings(val letters: Int, val rounds: Int, val constraintPolicy: ConstraintPolicy = ConstraintPolicy.IGNORE) {
    init {
        if (letters <= 0) throw IllegalArgumentException("Must have positive letter count")
        if (rounds <= 0) throw IllegalArgumentException("Must have positive round count")
    }
}