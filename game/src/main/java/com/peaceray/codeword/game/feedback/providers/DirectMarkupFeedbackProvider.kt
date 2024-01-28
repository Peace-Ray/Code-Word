package com.peaceray.codeword.game.feedback.providers

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.Feedback
import kotlin.math.max
import kotlin.math.min
import java.lang.UnsupportedOperationException

/**
 * Provides feedback only as far as characters have been directly annotated by markup. I.e., if
 * a character has been given "EXACT" markup, indicate that. If a character has been given
 * "INCLUDED" markup but can be inferred (e.g. through positional candidate analysis) to be
 * exactly located at some specific position, still give it only "EXACT" markup.
 *
 * This is a fairly naive analysis, as it does not directly compare the markup between different
 * Constraints to make inferences not directly visible in either individually. A letter will
 * only get marked "EXACT" if a Constraint has been provided designating it so, even if the letter
 * is known to occur and there is only one possible position for it.
 */
class DirectMarkupFeedbackProvider(
    characters: Set<Char>,
    length: Int,
    maxOccurrences: Int = length
): CachingFeedbackProvider(characters, length, 0..maxOccurrences) {

    override fun supports(policy: ConstraintPolicy) = policy in setOf(
        ConstraintPolicy.POSITIVE,
        ConstraintPolicy.ALL,
        ConstraintPolicy.PERFECT
    )

    override fun constrainFeedback(
        feedback: Pair<Feedback, Map<Char, CharacterFeedback>>,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Pair<Feedback, Map<Char, CharacterFeedback>> {
        if (!supports(policy)) {
            throw UnsupportedOperationException("Cannot provide feedback for policy ${policy}")
        }

        val candidates = feedback.first.candidates.map { it.toMutableSet() }.toMutableList()
        val occurrences = feedback.first.occurrences.toMutableMap()
        val markup = feedback.second.mapValues { it.value.markup }.toMutableMap()

        for (constraint in freshConstraints) {
            val zipped = constraint.candidate.toCharArray().zip(constraint.markup)

            // update candidates for each position based on direct markup.
            zipped.forEachIndexed { index, pair ->
                when (pair.second) {
                    Constraint.MarkupType.EXACT -> candidates[index] = mutableSetOf(pair.first)
                    Constraint.MarkupType.INCLUDED,
                    Constraint.MarkupType.NO -> candidates[index].remove(pair.first)
                }
            }

            // update occurrences for all letters
            characters.forEach { c ->
                val cZipped = zipped.filter { it.first == c }
                val eiCount = cZipped.count { it.second != Constraint.MarkupType.NO }

                val range = occurrences[c] ?: 0..0
                occurrences[c] = range.bound(
                    // minimum: number of exact and included markup, or number of exact candidates so far
                    minimum = setOf(eiCount, candidates.count { it.size == 1 && c in it }),
                    // maximum: number of direct and included markup if NO appears, number of possible candidates so far
                    maximum = setOf(
                        if (eiCount == cZipped.size) range.last else eiCount,
                        candidates.count { c in it }
                    )
                )
            }

            // update markup to the best available for each attempted letter
            constraint.candidate.toSet().forEach { c ->
                val cMarkup = markup[c]
                val zMarkup = zipped.filter { it.first == c }.map { it.second }.maxByOrNull { it.value() }
                markup[c] = listOf(cMarkup, zMarkup).maxByOrNull { it?.value() ?: -1 }
            }
        }

        // convert format
        return Pair(
            Feedback(
                candidates = candidates.map { it.toSet() }.toList(),
                occurrences = occurrences.toMap()
            ),
            characters.associateWith { c -> CharacterFeedback(
                c,
                occurrences[c] ?: 0..0,
                positions = (0 until length).filter { candidates[it].size == 1 && c in candidates[it] }.toSet(),
                absences = (0 until length).filter { c !in candidates[it] }.toSet(),
                markup[c]
            ) }
        )
    }

    private fun Constraint.MarkupType.value() = when(this) {
        Constraint.MarkupType.EXACT -> 2
        Constraint.MarkupType.INCLUDED -> 1
        Constraint.MarkupType.NO -> 0
    }

    private fun IntRange.bound(minimum: Collection<Int> = emptySet(), maximum: Collection<Int> = emptySet()): IntRange {
        if (minimum.isEmpty() && maximum.isEmpty()) {
            return this
        }

        val firstValue = min(last, minimum.fold(first) { a, b -> max(a, b) })
        val lastValue = max(first, maximum.fold(last) { a, b -> min(a, b) })

        return firstValue..lastValue
    }
}