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
 * This Provider works with all ConstraintPolicies, but those that are not byLetter will never
 * have EXACT or INCLUDED markup applied to them, as that markup is not "direct" unless applied
 * to the letter itself. Aggregated (byWord) policies will still mark letters as NO, when appropriate,
 * if sufficient information has been given to fully eliminate a letter from consideration.
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
        ConstraintPolicy.IGNORE,
        ConstraintPolicy.AGGREGATED,
        ConstraintPolicy.AGGREGATED_INCLUDED,
        ConstraintPolicy.AGGREGATED_EXACT,
        ConstraintPolicy.POSITIVE,
        ConstraintPolicy.ALL,
        ConstraintPolicy.PERFECT
    )

    override fun constrainFeedback(
        feedback: Feedback,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)?
    ): Feedback {
        if (!supports(policy)) {
            throw UnsupportedOperationException("Cannot provide feedback for policy ${policy}")
        }

        val candidates = feedback.candidates.map { it.toMutableSet() }.toMutableList()
        val occurrences = feedback.occurrences.toMutableMap()
        val markup = feedback.characters.mapValues { it.value.markup }.toMutableMap()

        freshConstraints.forEachIndexed { index, constraint ->
            val changed = when (policy) {
                ConstraintPolicy.IGNORE -> false
                ConstraintPolicy.AGGREGATED_EXACT,
                ConstraintPolicy.AGGREGATED_INCLUDED,
                ConstraintPolicy.AGGREGATED -> {
                    val considerIncluded = policy in setOf(ConstraintPolicy.AGGREGATED_INCLUDED, ConstraintPolicy.AGGREGATED)
                    val considerExact = policy in setOf(ConstraintPolicy.AGGREGATED_EXACT, ConstraintPolicy.AGGREGATED)
                    constrainFeedbackDirectlyByAggregated(
                        candidates,
                        occurrences,
                        markup,
                        considerIncluded = considerIncluded,
                        considerExact = considerExact,
                        constraint
                    )
                }
                ConstraintPolicy.POSITIVE,
                ConstraintPolicy.ALL,
                ConstraintPolicy.PERFECT -> {
                    constrainFeedbackDirectlyByLetter(candidates, occurrences, markup, constraint)
                }
            }

            // callback only if markup changed and there are more constraints to consider
            // (if not, the callback will soon be invoked by the superclass).
            if (callback != null && changed && index < freshConstraints.size - 1) {
                callback(asFeedback(candidates, occurrences, markup), false)
            }
        }

        return asFeedback(candidates, occurrences, markup)
    }



    private fun constrainFeedbackDirectlyByLetter(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markup: MutableMap<Char, Constraint.MarkupType?>,
        constraint: Constraint
    ): Boolean {
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
        var changed = false
        constraint.candidate.toSet().forEach { c ->
            val cMarkup = markup[c]
            val zMarkup = zipped.filter { it.first == c }.map { it.second }.maxByOrNull { it.value() }
            val nMarkup = listOf(cMarkup, zMarkup).maxByOrNull { it?.value() ?: -1 }
            if (nMarkup != cMarkup) {
                markup[c] = nMarkup
                changed = true
            }
        }

        return changed
    }

    private fun constrainFeedbackDirectlyByAggregated(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markup: MutableMap<Char, Constraint.MarkupType?>,
        considerIncluded: Boolean,
        considerExact: Boolean,
        constraint: Constraint
    ): Boolean {
        when {
            // if NO, none of these letters are correct
            considerIncluded && constraint.exact == 0 && constraint.included == 0 -> {
                constraint.candidate.toSet().forEach { char ->
                    // remove from ALL position candidates
                    candidates.forEach { candidateSet -> candidateSet.remove(char) }

                    // remove from ALL occurrences
                    occurrences[char] = 0..0
                }
            }

            // if no exact, no letter occurs at the given position
            considerExact && constraint.exact == 0 -> {
                constraint.candidate.forEachIndexed { index, char ->
                    // from from position candidates
                    val removed = candidates[index].remove(char)

                    // update occurrences
                    if (removed) {
                        val range = occurrences[char] ?: 0..0
                        occurrences[char] = range.bound(maximum = setOf(candidates.count { char in it }))
                    }
                }
            }

            // if has exact, but not included, nothing to do as it requires inference from other info.
        }

        // update markup from null to NO if a letter is fully eliminated. Will only happen
        // for letters in this constraint.
        var changed = false
        constraint.candidate.toSet().forEach { char ->
            if ((occurrences[char] ?: 0..0).last <= 0 && markup[char] == null) {
                changed = true
                markup[char] = Constraint.MarkupType.NO
            }
        }
        return changed
    }

    private fun asFeedback(
        candidates: List<Set<Char>>,
        occurrences: Map<Char, IntRange>,
        markup: Map<Char, Constraint.MarkupType?>
    ): Feedback {
        return Feedback(
            candidates = candidates.map { it.toSet() }.toList(),
            occurrences = occurrences.toMap(),
            markup = markup.toMap()
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