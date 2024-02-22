package com.peaceray.codeword.presentation.datamodel.guess

import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.presentation.datamodel.guess.GuessMarkup.Companion.toGuessMarkup

data class GuessAlphabet(
    val characters: Map<Char, Letter>
) {
    constructor(characters: Collection<Letter>): this(characters = characters.associateBy { it.character })

    constructor(characters: Collection<Char>, occurrences: IntRange):
            this(characters.associateWith { char -> Letter(char, occurrences ) })

    init {
        if (characters.any { it.key != it.value.character }) {
            throw IllegalArgumentException("characters map keys must match value characters")
        }
    }

    data class Letter(
        val character: Char,
        val occurrences: IntRange,
        val positions: Set<Int> = emptySet(),
        val absences: Set<Int> = emptySet(),
        val markup: GuessMarkup = when {
            positions.isNotEmpty() -> GuessMarkup.EXACT
            occurrences.last == 0 -> GuessMarkup.NO
            occurrences.first > 0 -> GuessMarkup.INCLUDED
            else -> GuessMarkup.EMPTY
        }
    ) {
        constructor(
            characterFeedback: CharacterFeedback,
            character: Char? = null,
            occurrences: IntRange? = null,
            positions: Set<Int>? = null,
            absences: Set<Int>? = null,
            markup: GuessMarkup? = null
        ): this(
            character = character ?: characterFeedback.character,
            occurrences = occurrences ?: characterFeedback.occurrences,
            positions = positions ?: characterFeedback.positions,
            absences = absences ?: characterFeedback.absences,
            markup = markup ?: characterFeedback.markup?.toGuessMarkup() ?: GuessMarkup.EMPTY
        )
    }
}