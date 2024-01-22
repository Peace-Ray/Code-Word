package com.peaceray.codeword.game.feedback.providers

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.game.feedback.ConstraintFeedbackPolicy
import com.peaceray.codeword.game.feedback.Feedback
import java.lang.UnsupportedOperationException
import kotlin.math.max
import kotlin.math.min

/**
 * Provides feedback based on direct markup annotation, if available, as well as inference from
 * eliminated characters and positional candidates. Simply put: does everything
 * [DirectMarkupFeedbackProvider] does, and in addition, infers markup and candidates when a character
 * has been completely eliminated. e.g. given these two Constraints:
 *
 * ABCD    (i = 1, e = 0)
 * ABCA    (i = 0, e = 0)
 *
 * will eliminate A, B, and C (giving them markup NO) and grant D the markup INCLUDED.
 *
 * Inferred markup is applied to CharacterFeedback, along with any direct markup provided.
 * Note that inferred markup will only be applied if it appears in the "markup" setting.
 *
 * More complicated inference is possible by comparing Constraints but is not attempted by this
 * Provider. Only the complete elimination of a candidate letter prompts this kind of reevaluation.
 */
class InferredMarkupFeedbackProvider(
    characters: Set<Char>,
    length: Int,
    maxOccurrences: Int = length,
    val markupPolicies: Set<MarkupPolicy> = setOf(MarkupPolicy.DIRECT)
): CachingFeedbackProvider(characters, length, 0..maxOccurrences) {

    enum class MarkupPolicy {
        /**
         * Apply markup that is directly indicated on a per-character basis by Constraints.
         */
        DIRECT,

        /**
         * Apply NO markup for any letter that has it directly indicated per-character, and
         * for all chars in a word that has 0 included matches.
         */
        DIRECT_ELIMINATION,

        /**
         * Apply markup that can be determined based on all Constraints provided, even if no individual
         * Constraint specified it.
         */
        INFERRED,

        /**
         * Apply NO markup for any letter that has been determined to not exist within the word,
         * even if no individual Constraint indicated this.
         */
        INFERRED_ELIMINATION;

        // TODO more markup policies, e.g. INDIRECT_* that check candidates and occurrences lists, not just Constraint responses.

        companion object {
            val directPolicies = setOf(DIRECT, DIRECT_ELIMINATION)
            val inferredPolicies = setOf(INFERRED, INFERRED_ELIMINATION)
        }
    }

    override fun supports(policy: ConstraintFeedbackPolicy) = policy in setOf(
        ConstraintFeedbackPolicy.CHARACTER_MARKUP,
        ConstraintFeedbackPolicy.AGGREGATED_MARKUP,
        ConstraintFeedbackPolicy.COUNT_EXACT,
        ConstraintFeedbackPolicy.COUNT_INCLUDED
    )

    override fun constrainFeedback(
        feedback: Pair<Feedback, Map<Char, CharacterFeedback>>,
        policy: ConstraintFeedbackPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Pair<Feedback, Map<Char, CharacterFeedback>> {
        if (!supports(policy)) {
            throw UnsupportedOperationException("Cannot provide feedback for policy ${policy}")
        }

        // make mutable
        val candidates = feedback.first.candidates.map { it.toMutableSet() }.toMutableList()
        val occurrences = feedback.first.occurrences.toMutableMap()
        val markups = feedback.second.mapValues { it.value.markup }.toMutableMap()

        // update mutable fields
        var changed = constrainCandidatesAndOccurrences(candidates, occurrences, policy, freshConstraints)
        while (changed) {
            changed = constrainCandidatesAndOccurrences(candidates, occurrences, policy, constraints.distinct())
        }
        inferMarkup(candidates, occurrences, markups, policy, freshConstraints)

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
                markups[c]
            ) }
        )
    }

    private fun constrainCandidatesAndOccurrences(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        policy: ConstraintFeedbackPolicy,
        constraints: List<Constraint>
    ): Boolean {
        val f: (c: Constraint) -> Boolean = when (policy) {
            ConstraintFeedbackPolicy.CHARACTER_MARKUP ->
                { c: Constraint -> constrainCandidatesAndOccurrencesCharacterMarkup(candidates, occurrences, c) }
            ConstraintFeedbackPolicy.AGGREGATED_MARKUP ->
                { c: Constraint -> constrainCandidatesAndOccurrencesAggregatedMarkup(candidates, occurrences, c) }
            ConstraintFeedbackPolicy.COUNT_INCLUDED ->
                { c: Constraint -> constrainCandidatesAndOccurrencesCountIncluded(candidates, occurrences, c) }
            ConstraintFeedbackPolicy.COUNT_EXACT ->
                { c: Constraint -> constrainCandidatesAndOccurrencesCountExact(candidates, occurrences, c) }
        }

        return constraints.fold(false) { changed, c -> f(c) || changed }
    }

    private fun constrainCandidatesAndOccurrencesCharacterMarkup(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        constraint: Constraint
    ): Boolean {
        val zipped = constraint.candidate.toCharArray().zip(constraint.markup)

        var changed = false

        // update candidates for each position based on direct markup.
        zipped.forEachIndexed { index, pair ->
            when (pair.second) {
                Constraint.MarkupType.EXACT -> {
                    if (candidates[index].size != 1) changed = true
                    candidates[index] = mutableSetOf(pair.first)
                }
                Constraint.MarkupType.INCLUDED,
                Constraint.MarkupType.NO -> {
                    if (candidates[index].remove(pair.first)) changed = true
                }
            }
        }

        // update occurrences for all letters
        characters.forEach { c ->
            val cZipped = zipped.filter { it.first == c }
            val eiCount = cZipped.count { it.second != Constraint.MarkupType.NO }

            val range = occurrences[c] ?: 0..0
            val bounded = range.bound(
                // minimum: number of exact and included markup, or number of exact candidates so far
                minimum = setOf(eiCount, candidates.count { it.size == 1 && c in it }),
                // maximum: number of direct and included markup if NO appears, number of possible candidates so far
                maximum = setOf(
                    if (eiCount == cZipped.size) range.last else eiCount,
                    candidates.count { c in it }
                )
            )
            if (range.first != bounded.first || range.last != bounded.last) {
                occurrences[c] = bounded
                changed = true
            }
        }

        return changed
    }

    private fun constrainCandidatesAndOccurrencesAggregatedMarkup(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        constraint: Constraint
    ): Boolean {
        val word = constraint.candidate
        val chars = word.toSet()
        val pos = candidates.indices
        val eiCount = constraint.exact + constraint.included
        val noCount = length - eiCount

        // iteratively constrain possibilities by removing candidates and occurrences;
        // repeat until nothing changes
        var everChanged = false
        var changed: Boolean
        do {
            changed = false

            // Count necessary appearances: the number of appearances, for each letter, that must occur.
            val necessaryAppearances = chars.associateWith { char ->
                min(word.count { it == char }, occurrences[char]?.first ?: 0)
            }
            val necessaryAppearancesSum = necessaryAppearances.values.sum()

            // A character is "necessary" if it appears no more times than it absolutely must.
            // A character is "overrepresented" if it appears more times than it possibly could.
            // Determine this based on the max occurrences bound for the letter, the minimum
            // occurrences of all other letters (leftover "space" for any other), and the
            // number of exact/included counts that are not explained by "necessaryAppearances".
            val requiredCount = occurrences.values.sumOf { it.first }
            val appearances = chars.associateWith { char -> constraint.candidate.count { it == char } }
            val maxAppearances = chars.associateWith { char ->
                val range = occurrences[char] ?: 0..0
                val space = length - (requiredCount - range.first)

                val eiAvailable = eiCount - (necessaryAppearancesSum - (necessaryAppearances[char] ?: 0))
                min(min(range.last, space), eiAvailable)
            }

            val necessaryChars = chars.filter { char ->
                word.count { it == char } == (occurrences[char]?.first ?: 0)
            }
            val overrepresentedChars = chars.filter { char ->
                (appearances[char] ?: 0) > (maxAppearances[char] ?: 0)
            }

            // An "overrepresented" character can reveal information about character placement.
            overrepresentedChars.forEach { char ->
                if (eiCount == necessaryAppearancesSum - (necessaryAppearances[char] ?: 0)) {
                    // "exact" and "included" are fully represented by other characters;
                    // this character cannot appear anywhere.
                    pos.forEach {  if (candidates[it].remove(char)) changed = true }
                } else if (constraint.included == 0) {
                    // TODO consider more complicated analysis to determine if nonzero "constraint.included"
                    // can be explained by other characters in the word.
                    // The character cannot appear anywhere other than where it was written
                    // (but any one of those locations might be incorrect).
                    pos.filter { word[it] != char }.forEach { if (candidates[it].remove(char)) changed = true }
                }
            }

            // A "necessary" character can reveal information about character placement.
            // If "constraint.included" can be fully explained by other letters, it must
            // appear at all current positions.

            necessaryChars.forEach { char ->
                val otherExactCount = pos.count { index ->
                    val otherChar = word[index]
                    otherChar != char && candidates[index].size == 1 && char in candidates[index]
                }
                if (constraint.exact == otherExactCount) {
                    // If "constraint.exact" can be fully explained by other letters, this char must not
                    // appear at any current position.
                    pos.filter { word[it] == char }.forEach { if (candidates[it].remove(char)) changed = true }
                }

                if (constraint.included == 0) {
                    // TODO consider more complicated analysis to determine if nonzero "constraint.included"
                    // can be explained by other characters in the word.
                    // Since "constraint.included" can be fully explained by other letters, it
                    // must appear at all current positions.
                    pos.filter { word[it] == char }.forEach {
                        if (candidates[it].size != 1) {
                            candidates[it] = mutableSetOf(char)
                            changed = true
                        }
                    }
                }
            }

            // revise bounds on occurrences for each character based on candidate sets.
            // update occurrences
            characters.forEach { char ->
                val range = occurrences[char] ?: 0..0
                val boundedRange = range.bound(
                    // minimum bounds: the number of known candidate positions,
                    // and the number of exact or included matches remaining after other letters are accounted for
                    minimum = setOf(
                        candidates.count { char in it && it.size == 1 },
                        (constraint.exact + constraint.included) - constraint.candidate.count { c -> c != char }
                    ),
                    // maximum bounds: the number of possible candidate positions,
                    // the non-exact count if the letter does not occur
                    maximum = setOf(
                        candidates.count { char in it },
                        if (constraint.candidate.count { it == char } != 0) length else noCount
                    )
                )
                occurrences[char] = boundedRange

                changed = range.first != boundedRange.first || range.last != boundedRange.last
            }

            // Note: we have strictly more information than the IncludedCount and ExactCount cases,
            // so performing their calculations is appropriate here to consider analysis we skipped.
            // Only bother doing so if the more sophisticated analysis did not produce a change.
            if (!changed && constrainCandidatesAndOccurrencesCountIncluded(candidates, occurrences, constraint)) changed = true
            if (!changed && constrainCandidatesAndOccurrencesCountExact(candidates, occurrences, constraint)) changed = true

            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainCandidatesAndOccurrencesCountIncluded(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        constraint: Constraint
    ): Boolean {
        val inCount = constraint.exact + constraint.included
        val noCount = constraint.candidate.length - inCount
        val letters = constraint.candidate.toList()

        // iteratively constrain possibilities by removing candidates and occurrences;
        // repeat until nothing changes.
        var everChanged = false
        var changed: Boolean
        do {
            changed = false

            // locate known included letters, and track those not in that list
            val inLetters = mutableListOf<Char>()
            val notInLetters = mutableListOf<Char>()
            letters.forEach { c ->
                val minCount = (occurrences[c] ?: 0..0).first
                if (inLetters.count { it == c } < minCount) {
                    inLetters.add(c)
                } else {
                    notInLetters.add(c)
                }
            }

            // find non-candidates.
            // if the number of "included letters" matches inCount, any other letter is considered
            // not included. More generally, if a letter appears more times in "not included letters"
            // than the number of "unexplained inCounts" remaining, only the number of unexplained inCounts
            // remaining could possibly be included.
            //
            // This has two effects: constraining the maximum occurrences, and
            // (if fully eliminated) eliminating it as a candidate.
            val remainingInCount = inCount - inLetters.size
            notInLetters.distinct().forEach { char ->
                val cInLetters = inLetters.count{ it == char }
                val cNotInLetters = notInLetters.count{ it == char }
                if (cNotInLetters > remainingInCount) {
                    val maximumOccurrences = cInLetters + remainingInCount
                    // set the new maximum occurrences
                    val range = occurrences[char] ?: 0..0
                    val bounded = range.bound(maximum = setOf(cInLetters + remainingInCount))
                    if (range.last != bounded.last) {
                        occurrences[char] = bounded
                        changed = true
                    }

                    // eliminate as a candidate universally
                    if (maximumOccurrences == 0) {
                        candidates.forEach { if (it.remove(char)) changed = true }
                    }
                }
            }

            // locate known not-included letters, and track those not in that list
            val absLetters = mutableListOf<Char>()
            val notAbsLetters = mutableListOf<Char>()
            letters.forEach { c ->
                val maxCount = (occurrences[c] ?: 0..0).last
                if (notAbsLetters.count { it == c } >= maxCount) {
                    absLetters.add(c)
                } else {
                    notAbsLetters.add(c)
                }
            }

            // find included matches: if the number of "not included letters" matches noCount, any
            // other letter is included. This constrains the minimum occurrences.
            if (absLetters.size == noCount) {
                notAbsLetters.distinct().forEach { char ->
                    val range = occurrences[char] ?: 0..0
                    val bounded = range.bound(minimum = setOf(notAbsLetters.count { it == char }))
                    if (range.first != bounded.first) {
                        occurrences[char] = bounded
                        changed = true
                    }
                }
            }
            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainCandidatesAndOccurrencesCountExact(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        constraint: Constraint
    ): Boolean {
        val noCount = constraint.candidate.length - constraint.exact
        val pos = candidates.indices

        // iteratively constraint possibilities by removing candidates and occurrences;
        // repeat until nothing changes.
        var everChanged = false
        var changed: Boolean
        do {
            changed = false

            // locate known exact positions
            val ePos = pos.filter { i -> candidates[i].size == 1 && constraint.candidate[i] in candidates[i] }
            // find non-candidates: if the number of "exact positions" matches the EXACT count, any
            // other character is nonExact.
            if (ePos.size == constraint.exact) {
                pos.filter { i -> i !in ePos }.forEach { i ->
                    val char = constraint.candidate[i]
                    if (candidates[i].remove(char)) changed = true
                }
            }

            // locate known non-exact positions
            val nPos = pos.filter { i -> constraint.candidate[i] !in candidates[i] }
            // find candidates: if number of "non-exact positions" matches the NO count, any
            // other character is exact.
            if (nPos.size == noCount) {
                pos.filter { i -> i !in nPos }.forEach { i ->
                    val char = constraint.candidate[i]
                    if (candidates[i].size != 1) changed = true
                    candidates[i] = mutableSetOf(char)
                }
            }

            // update occurrences
            characters.forEach { char ->
                val range = occurrences[char] ?: 0..0
                val boundedRange = range.bound(
                    // minimum bounds: the number of known candidate positions,
                    // and the number of "exact" matches remaining after other letters are accounted for
                    minimum = setOf(
                        candidates.count { char in it && it.size == 1 },
                        constraint.exact - constraint.candidate.count { c -> c != char }
                    ),
                    // maximum bounds: the number of possible candidate positions,
                    // the non-exact count if the letter does not occur
                    maximum = setOf(
                        candidates.count { char in it },
                        if (constraint.candidate.count { it == char } != 0) length else noCount
                    )
                )
                occurrences[char] = boundedRange

                changed = range.first != boundedRange.first || range.last != boundedRange.last
            }

            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun inferMarkup(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        constraintFeedbackPolicy: ConstraintFeedbackPolicy,
        constraints: List<Constraint>
    ): Boolean {
        var changed = false

        // apply explicitly indicated markup as indicated by markup policy
        if (constraintFeedbackPolicy == ConstraintFeedbackPolicy.CHARACTER_MARKUP
            && markupPolicies.intersect(MarkupPolicy.directPolicies).isNotEmpty()) {

            constraints.forEach { constraint ->
                val zipped = constraint.candidate.toCharArray().zip(constraint.markup)

                // update markup to the best available for each attempted letter
                constraint.candidate.toSet().forEach { c ->
                    val zMarkup = zipped.filter { it.first == c }
                        .filter {
                            MarkupPolicy.DIRECT in markupPolicies
                                    || (MarkupPolicy.DIRECT_ELIMINATION in markupPolicies && it.second == Constraint.MarkupType.NO)
                        }
                        .map { it.second }.maxByOrNull { it.value() }
                    val cMarkup = markups[c]

                    val newMarkup = listOf(cMarkup, zMarkup).maxByOrNull { it?.value() ?: -1 }
                    if (newMarkup != cMarkup) {
                        markups[c] = newMarkup
                        changed = true
                    }
                }
            }
        }

        // DIRECT ELIMINATION: if a constraint has all NOs, eliminate all candidates.
        if (constraintFeedbackPolicy != ConstraintFeedbackPolicy.COUNT_EXACT
            && markupPolicies.intersect(MarkupPolicy.directPolicies).isNotEmpty()) {

            constraints.forEach { constraint ->
                if (constraint.exact + constraint.included == 0) {
                    constraint.candidate.toSet().forEach { c ->
                        val cMarkup = markups[c]
                        val newMarkup = listOf(cMarkup, Constraint.MarkupType.NO).maxByOrNull { it?.value() ?: -1 }
                        if (newMarkup != cMarkup) {
                            markups[c] = newMarkup
                            changed = true
                        }
                    }
                }
            }
        }

        // INFERRED MARKUP
        if (markupPolicies.intersect(MarkupPolicy.inferredPolicies).isNotEmpty()) {
            // elimination: any character with a max of 0 occurrences is marked NO
            occurrences.filter { it.value.last == 0 }.keys.forEach { c ->
                if (markups[c] != Constraint.MarkupType.NO) {
                    markups[c] = Constraint.MarkupType.NO
                    changed = true
                }
            }

            if (MarkupPolicy.INFERRED in markupPolicies) {
                // also infer INCLUDED and EXACT.
                occurrences.filter { it.value.first > 0 }.keys.forEach { c ->
                    // a letter that definitely appears. but should it be EXACT or INCLUDED?
                    val oMarkup = when (constraintFeedbackPolicy) {
                        ConstraintFeedbackPolicy.CHARACTER_MARKUP,
                        ConstraintFeedbackPolicy.AGGREGATED_MARKUP -> {
                            if (candidates.any { it.size == 1 && c in it }) {
                                Constraint.MarkupType.EXACT
                            } else {
                                Constraint.MarkupType.INCLUDED
                            }
                        }
                        ConstraintFeedbackPolicy.COUNT_INCLUDED -> Constraint.MarkupType.INCLUDED
                        ConstraintFeedbackPolicy.COUNT_EXACT -> Constraint.MarkupType.EXACT
                    }

                    val cMarkup = markups[c]

                    val newMarkup = listOf(cMarkup, oMarkup).maxByOrNull { it?.value() ?: -1 }
                    if (newMarkup != cMarkup) {
                        markups[c] = newMarkup
                        changed = true
                    }
                }
            }
        }

        return changed
    }

    private fun IntRange.bound(minimum: Collection<Int> = emptySet(), maximum: Collection<Int> = emptySet()): IntRange {
        if (minimum.isEmpty() && maximum.isEmpty()) {
            return this
        }

        val firstValue = min(last, minimum.fold(first) { a, b -> max(a, b) })
        val lastValue = max(first, maximum.fold(last) { a, b -> min(a, b) })

        return firstValue..lastValue
    }

    private fun Constraint.MarkupType.value() = when(this) {
        Constraint.MarkupType.EXACT -> 2
        Constraint.MarkupType.INCLUDED -> 1
        Constraint.MarkupType.NO -> 0
    }
}