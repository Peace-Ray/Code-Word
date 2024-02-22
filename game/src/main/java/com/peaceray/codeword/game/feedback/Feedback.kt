package com.peaceray.codeword.game.feedback

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.validators.Validator

/**
 * A class that contains and evaluates all available Constraints from an ongoing game, according
 * to some ConstraintFeedbackPolicy, and makes the combined information available in a number of
 * forms -- including as word Validator and per-character evaluation.
 */
data class Feedback(
    /**
     * The characters available for each position.
     */
    val candidates: List<Set<Char>>,

    /**
     * The feedback -- markup and positioning -- for each character.
     */
    val characters: Map<Char, CharacterFeedback>
) {

    constructor(candidates: List<Set<Char>>, occurrences: Map<Char, IntRange>, markup: Map<Char, Constraint.MarkupType?>): this(
        candidates = candidates,
        characters = occurrences.keys.associateWith { char ->
            CharacterFeedback(
                char,
                occurrences[char] ?: 0..0,
                candidates.indices.filter { char in candidates[it] && candidates[it].size == 1 }.toSet(),
                candidates.indices.filter { char !in candidates[it] }.toSet(),
                markup[char]
            )
        }
    )

    /**
     * Construct an empty Feedback instance based on the available characters and word length.
     *
     * @param characters The alphabet available for
     */
    constructor(characters: Set<Char>, length: Int, occurrences: IntRange = 0..length): this(
        candidates = List(length) { if (occurrences.last > 0) characters else emptySet() },
        characters = characters.associateWith { char ->
            CharacterFeedback(
                char,
                occurrences,
                emptySet(),
                emptySet(),
                null
            )
        }
    )

    val occurrences: Map<Char, IntRange> = characters.mapValues { entry -> entry.value.occurrences }

    /**
     * Characters known to occur in a particular spot.
     */
    val exact: List<Char?> = candidates.map { if (it.size == 1) it.first() else null }

    /**
     * Characters which are known to be included in the word. A character that appears multiple
     * times on this list are known to be included at least that many times.
     */
    val included: List<Char> = occurrences.filter { it.value.first > 0 }.map { List(it.value.first) { _ -> it.key } }.flatten()

    /**
     * Characters which are known to NOT be included in the word, in ANY position.
     */
    val absent: Set<Char> = occurrences.filter { it.value.last == 0 }.map { it.key }.toSet()

    /**
     * A Validator that reports whether a given word is consistent with the Feedback received thus far.
     */
    val validator: Validator by lazy {
        object: Validator {
            override fun invoke(code: String): Boolean {
                // character positions: check that those characters are available in those places
                if (!code.foldIndexed(true) { index, acc, c -> acc && c in candidates[index] }) return false

                // occurrences: check that no character in the code is under or over-represented
                val codeChars = code.toSet()
                val codeCharCount = codeChars.associateWith { c -> code.count { it == c } }
                if (!codeCharCount.all { it.value in (occurrences[it.key] ?: -1..-1) }) return false

                // occurrences: check that no character not included in the code is required
                if (occurrences.any { it.key !in codeChars && it.value.first > 0 }) return false

                return true
            }
        }
    }
}