package com.peaceray.codeword.game.feedback.providers

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
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
        INFERRED_ELIMINATION,

        /**
         * Apply markup when a [Constraint.correct] constraint is provided.
         */
        SOLUTION,

        /**
         * Apply NO markup when a [Constraint.correct] constraint is provided.
         */
        SOLUTION_ELIMINATION;

        companion object {
            val directPolicies = setOf(DIRECT, DIRECT_ELIMINATION)
            val inferredPolicies = setOf(INFERRED, INFERRED_ELIMINATION)
            val solutionPolicies = setOf(SOLUTION, SOLUTION_ELIMINATION)
            val eliminationPolicies = setOf(DIRECT_ELIMINATION, INFERRED_ELIMINATION, SOLUTION_ELIMINATION)
        }
    }

    override fun supports(policy: ConstraintPolicy) = policy in setOf(
        ConstraintPolicy.PERFECT,
        ConstraintPolicy.ALL,
        ConstraintPolicy.POSITIVE,
        ConstraintPolicy.AGGREGATED,
        ConstraintPolicy.AGGREGATED_EXACT,
        ConstraintPolicy.AGGREGATED_INCLUDED
    )

    override fun constrainFeedback(
        feedback: Feedback,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>,
        callback: ((feedback: Feedback, done: Boolean) -> Boolean)?
    ): Feedback {
        if (!supports(policy)) {
            throw UnsupportedOperationException("Cannot provide feedback for policy $policy")
        }

        // make mutable
        val candidates = feedback.candidates.map { it.toMutableSet() }.toMutableList()
        val occurrences = feedback.occurrences.toMutableMap()
        val markups = feedback.characters.mapValues { it.value.markup }.toMutableMap()

        // check markup policies
        val doSolutionMarkup = markupPolicies.intersect(MarkupPolicy.solutionPolicies).isNotEmpty()
        val doIndirectMarkup = markupPolicies.intersect(MarkupPolicy.inferredPolicies).isNotEmpty()
        val doDirectMarkup = doIndirectMarkup || markupPolicies.intersect(MarkupPolicy.directPolicies).isNotEmpty()
        val doIndirectNonElimination = MarkupPolicy.INFERRED in markupPolicies
        val doDirectNonElimination = doIndirectNonElimination || MarkupPolicy.DIRECT in markupPolicies
        val doSolutionNonElimination = doDirectNonElimination
                || MarkupPolicy.SOLUTION in markupPolicies

        // apply solution. Supa fast.
        if (doSolutionMarkup) {
            val solutionMarkupChanged = freshConstraints.any { constraint ->
                constrainFeedbackBySolution(candidates, occurrences, markups, doSolutionNonElimination, constraint)
            }

            // complete and perfect; no further modification will have any effect. No
            // callback; superclass will perform call.
            if (solutionMarkupChanged) {
                return asFeedback(candidates, occurrences, markups)
            }
        }

        // apply direct updates. Direct markup is fast and can be done with a single pass on each
        // unique Constraint; there is no need to ever recur.
        var directMarkupChanged = false
        if (doDirectMarkup) {
            freshConstraints.forEachIndexed { index, constraint ->
                val changed = constrainFeedbackDirectly(
                    candidates,
                    occurrences,
                    markups,
                    doDirectNonElimination,
                    policy,
                    constraint
                )
                directMarkupChanged = directMarkupChanged || changed
            }
        }

        // callback only if markup changed and there are more operations forthcoming
        // (otherwise the callback will be invoked by superclass upon exit).
        val moreForthcoming = freshConstraints.isNotEmpty() && markupPolicies.intersect(MarkupPolicy.inferredPolicies).isNotEmpty()
        if (callback != null && directMarkupChanged && moreForthcoming) {
            val feedback = asFeedback(candidates, occurrences, markups)
            if (callback(asFeedback(candidates, occurrences, markups), false)) {
                return feedback
            }
        }

        if (doIndirectMarkup) {
            // Indirect markup differs based on constraint policy.
            when (policy) {
                ConstraintPolicy.POSITIVE,
                ConstraintPolicy.ALL,
                ConstraintPolicy.PERFECT -> {
                    // every constraint has been fully represented in the feedback already;
                    // there is no information to be gained by reexamination. However,
                    // feedback can perhaps be improved by internal inference within the
                    // feedback fields themselves; e.g. by determining (based on elimination
                    // of other candidates) that a given letter must occur, even if it was
                    // not yet used in a Constraint.
                    constrainFeedbackIndirectlyByLetter(candidates, occurrences, markups, doIndirectNonElimination)
                    // no callback; superclass will handle it
                }
                ConstraintPolicy.AGGREGATED_EXACT,
                ConstraintPolicy.AGGREGATED_INCLUDED,
                ConstraintPolicy.AGGREGATED -> {
                    // aggregated constraints have complex comparisons; the result is dependent
                    // on the input state of the Feedback. For that reason, we iterate
                    // repeatedly until no changes are detected. Changes prompted by
                    // the fresh constraints may allow additional information to be gleaned from
                    // past constraints.
                    var changed = constrainFeedbackIndirectlyByAggregated(candidates, occurrences, markups, doIndirectNonElimination, policy, constraints, freshConstraints)
                    while (changed) {
                        // do a callback before the next iteration
                        if (callback != null) {
                            val feedback = asFeedback(candidates, occurrences, markups)
                            if (callback(asFeedback(candidates, occurrences, markups), false)) {
                                return feedback
                            }
                        }
                        // repeat constrain operation with the entire list of constraints
                        changed = constrainFeedbackIndirectlyByAggregated(candidates, occurrences, markups, doIndirectNonElimination, policy, constraints, constraints)
                    }
                }
                ConstraintPolicy.IGNORE -> {}
            }
        }

        // convert format and return. Superclass invokes the callback to finish.
        return asFeedback(candidates, occurrences, markups)
    }

    private fun constrainFeedbackDirectly(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups:  MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        policy: ConstraintPolicy,
        constraint: Constraint
    ) = when (policy) {
        ConstraintPolicy.IGNORE -> false
        ConstraintPolicy.AGGREGATED_EXACT,
        ConstraintPolicy.AGGREGATED_INCLUDED,
        ConstraintPolicy.AGGREGATED -> {
            val considerIncluded = policy in setOf(ConstraintPolicy.AGGREGATED_INCLUDED, ConstraintPolicy.AGGREGATED)
            val considerExact = policy in setOf(ConstraintPolicy.AGGREGATED_EXACT, ConstraintPolicy.AGGREGATED)
            constrainFeedbackDirectlyByAggregated(
                candidates,
                occurrences,
                markups,
                considerIncluded = considerIncluded,
                considerExact = considerExact,
                constraint
            )
        }
        ConstraintPolicy.POSITIVE,
        ConstraintPolicy.ALL,
        ConstraintPolicy.PERFECT -> {
            constrainFeedbackDirectlyByLetter(candidates, occurrences, markups, includeNonElimination, constraint)
        }
    }

    private fun constrainFeedbackBySolution(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        constraint: Constraint
    ): Boolean {
        // only if correct
        if (constraint.correct) {
            // set all candidates
            constraint.candidate.forEachIndexed { index, c ->
                candidates[index] = mutableSetOf(c)
            }

            // set all occurrences
            occurrences.keys.forEach { char ->
                val count = constraint.candidate.count { it == char }
                occurrences[char] = count..count
            }

            inferMarkups(candidates, occurrences, markups, includeNonElimination)
            return true
        }

        return false
    }

    private fun constrainFeedbackDirectlyByLetter(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups:  MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
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
                // minimum: number of exact and included markups, or number of exact candidates so far
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
            val cMarkup = markups[c]
            val zMarkup = zipped.filter { it.first == c }.map { it.second }.maxByOrNull { it.value() }
            val nMarkup = listOf(cMarkup, zMarkup).maxByOrNull { it?.value() ?: -1 }
            if (nMarkup != cMarkup && (includeNonElimination || nMarkup == Constraint.MarkupType.NO)) {
                markups[c] = nMarkup
                changed = true
            }
        }

        return changed
    }

    private fun constrainFeedbackDirectlyByAggregated(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups:  MutableMap<Char, Constraint.MarkupType?>,
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
                    // remove from position candidates
                    val removed = candidates[index].remove(char)

                    // update occurrences
                    if (removed) {
                        val range = occurrences[char] ?: 0..0
                        occurrences[char] = range.bound(maximum = setOf(candidates.count { char in it }))
                    }
                }
            }

            // if all exact, ALL letters are correct
            considerExact && constraint.exact == length -> {
                // set all candidates
                constraint.candidate.forEachIndexed { index, c ->
                    candidates[index] = mutableSetOf(c)
                }

                // set all occurrences
                occurrences.keys.forEach { char ->
                    val count = constraint.candidate.count { it == char }
                    occurrences[char] = count..count
                }
            }

            // if has exact, but not included, nothing to do as it requires inference from other info.
        }

        // update markup from null to NO if a letter is fully eliminated. Will only happen
        // for letters in this constraint.
        var changed = false
        constraint.candidate.toSet().forEach { char ->
            if ((occurrences[char] ?: 0..0).last <= 0 && markups[char] == null) {
                changed = true
                markups[char] = Constraint.MarkupType.NO
            }
        }
        return changed
    }

    private fun constrainFeedbackIndirectlyByLetter(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean
    ): Boolean {
        // candidates and occurrences can influence each other
        constrainCandidatesAndOccurrences(candidates, occurrences)

        // update markup to the best available for each attempted letter
        return inferMarkups(candidates, occurrences, markups, includeNonElimination)
    }

    private fun constrainFeedbackIndirectlyByAggregated(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups:  MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        policy: ConstraintPolicy,
        constraints: List<Constraint>,
        freshConstraints: List<Constraint>
    ): Boolean {
        val applyConstraint = { constraint: Constraint ->
            constrainFeedbackIndirectlyByAggregated(candidates, occurrences, markups, includeNonElimination, policy, constraint)
        }
        val compareConstraints = {
            constrainFeedbackIndirectlyByAggregatedConstraintComparison(candidates, occurrences, markups, includeNonElimination, policy, constraints)
        }

        var everChanged = false
        var changed: Boolean
        do {
            changed = false

            // it's cheaper to constrain based on individual constraints than by n^2 comparison,
            // so loop the former until no more changes occur before doing the latter.
            while (freshConstraints.any { applyConstraint(it) }) {
                changed = true
            }

            changed = compareConstraints() || changed
            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregated(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        policy: ConstraintPolicy,
        constraint: Constraint
    ): Boolean {
        val exact = { constrainFeedbackIndirectlyByAggregatedCountsExact(candidates, occurrences, markups, includeNonElimination, constraint) }
        val inclu = { constrainFeedbackIndirectlyByAggregatedCountsIncluded(candidates, occurrences, markups, includeNonElimination, constraint) }
        val aggre = { constrainFeedbackIndirectlyByAggregatedCounts(candidates, occurrences, markups, includeNonElimination, constraint) }
        val adjustments: List<()-> Boolean> = when (policy) {
            ConstraintPolicy.AGGREGATED_EXACT -> listOf(exact)
            ConstraintPolicy.AGGREGATED_INCLUDED -> listOf(inclu)
            ConstraintPolicy.AGGREGATED -> listOf(aggre, exact, inclu)
            else -> throw IllegalArgumentException("No support for policy $policy; must be AGGREGATED*")
        }

        var everChanged = false
        var changed: Boolean
        do {
            changed = adjustments.any { it() }
            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregatedCounts(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
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
                    otherChar != char && candidates[index].size == 1 && otherChar in candidates[index]
                }
                if (constraint.exact == otherExactCount) {
                    // If "constraint.exact" can be fully explained by other letters, this char must not
                    // appear at any current position.
                    pos.filter { word[it] == char }
                        .forEach { if (candidates[it].remove(char)) {
                            changed = true
                        } }
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
                    // minimum bounds: the number of exact or included matches remaining after other letters are accounted for
                    minimum = setOf(
                        (constraint.exact + constraint.included) - constraint.candidate.count { c -> c != char }
                    ),
                    // maximum bounds: the number of possible candidate positions,
                    // the non-exact count if the letter does not occur
                    maximum = setOf(if (constraint.candidate.count { it == char } != 0) length else noCount)
                )
                if (range.first != boundedRange.first || range.last != boundedRange.last) {
                    occurrences[char] = boundedRange
                    changed = true
                }
            }

            // allow new occurrences and candidates to constrain each other
            if (changed) constrainCandidatesAndOccurrences(candidates, occurrences)

            everChanged = everChanged || changed
        } while (changed)

        // update markup to the best available for all letters
        everChanged = inferMarkups(candidates, occurrences, markups, includeNonElimination) || everChanged
        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregatedCountsIncluded(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
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

            // allow new occurrences and candidates to constrain each other
            if (changed) constrainCandidatesAndOccurrences(candidates, occurrences)

            everChanged = everChanged || changed
        } while (changed)

        // update markup to the best available for all letters
        everChanged = inferMarkups(candidates, occurrences, markups, includeNonElimination) || everChanged
        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregatedCountsExact(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
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
                    if (candidates[i].remove(char)) {
                        changed = true
                    }
                }
            }

            // locate known non-exact positions
            val nPos = pos.filter { i -> constraint.candidate[i] !in candidates[i] }
            // find candidates: if number of "non-exact positions" matches the NO count, any
            // other character is exact.
            if (nPos.size == noCount) {
                pos.filter { i -> i !in nPos }.forEach { i ->
                    val char = constraint.candidate[i]
                    if (candidates[i].size != 1) {
                        changed = true
                        candidates[i] = mutableSetOf(char)
                    }
                }
            }

            // update occurrences
            characters.forEach { char ->
                val range = occurrences[char] ?: 0..0
                val boundedRange = range.bound(
                    // minimum bounds: the number of "exact" matches remaining after other letters are accounted for
                    minimum = setOf(constraint.exact - constraint.candidate.count { c -> c != char }),
                    // maximum bounds: the non-exact count if the letter does not occur
                    maximum = setOf(if (constraint.candidate.count { it == char } != 0) length else noCount)
                )

                if (range.first != boundedRange.first || range.last != boundedRange.last) {
                    occurrences[char] = boundedRange
                    changed = true
                }
            }

            // allow new occurrences and candidates to constrain each other
            if (changed) constrainCandidatesAndOccurrences(candidates, occurrences)

            everChanged = everChanged || changed
        } while (changed)

        // update markup to the best available for all letters
        everChanged = inferMarkups(candidates, occurrences, markups, includeNonElimination) || everChanged
        return everChanged
    }

    /**
     * Constraint candidates based on occurrences, and occurrences based on candidates.
     * For instance, the number of known exact positions is a lower-bound on occurrences,
     * while the number of possible positions is an upper bound. Similarly, if the number
     * of confirmed positions meets the upper occurrences bound, then the character is not
     * a candidate for any other position.
     */
    private fun constrainCandidatesAndOccurrences(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>
    ): Boolean {
        var everChanged = false
        var changed: Boolean
        do {
            changed = false

            // update occurrences based on candidates
            for (char in characters) {
                val range = occurrences[char] ?: 0..0
                val boundedRange = range.bound(
                    minimum = setOf( candidates.count { char in it && it.size == 1 } ),
                    maximum = setOf( candidates.count { char in it } )
                )
                if (range.first != boundedRange.first || range.last != boundedRange.last) {
                    occurrences[char] = boundedRange
                    changed = true
                }
            }

            // update occurrences based on other letter minimum occurrences
            val totalMinOccurrences = occurrences.values.sumOf { it.first }
            for (char in characters) {
                val range = occurrences[char] ?: 0..0
                val max = length - (totalMinOccurrences - range.first)
                if (max < range.last) {
                    occurrences[char] = range.bound(maximum = setOf(max))
                    changed = true
                }
            }

            // update candidates based on occurrences
            for (char in characters) {
                val range = occurrences[char] ?: 0..0
                val (exactPos, inexactPos) = candidates.indices
                    .filter { char in candidates[it] }
                    .partition { candidates[it].size == 1 }
                if (exactPos.size >= range.last && inexactPos.size > 1) {
                    // all positions are known; remove from inexact
                    inexactPos.forEach { candidates[it].remove(char) }
                    changed = true
                } else if (inexactPos.isNotEmpty() && inexactPos.size <= range.first - exactPos.size) {
                    // all inexact positions must be this character
                    inexactPos.forEach { candidates[it] = mutableSetOf(char) }
                    changed = true
                }
            }

            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregatedConstraintComparison(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        policy: ConstraintPolicy,
        constraints: List<Constraint>
    ): Boolean {
        val exact = { constrainFeedbackIndirectlyByAggregatedExactCountsConstraintComparison(candidates, occurrences, markups, includeNonElimination, constraints) }
        val inclu = { constrainFeedbackIndirectlyByAggregatedIncludedCountsConstraintComparison(candidates, occurrences, markups, includeNonElimination, constraints) }
        val aggre = { constrainFeedbackIndirectlyByAggregatedCountsConstraintComparison(candidates, occurrences, markups, includeNonElimination, constraints) }
        val adjustments: List<()-> Boolean> = when (policy) {
            ConstraintPolicy.AGGREGATED_EXACT -> listOf(exact)
            ConstraintPolicy.AGGREGATED_INCLUDED -> listOf(inclu)
            ConstraintPolicy.AGGREGATED -> listOf(aggre, exact, inclu)
            else -> throw IllegalArgumentException("No support for policy $policy; must be AGGREGATED*")
        }

        var everChanged = false
        var changed: Boolean
        do {
            changed = adjustments.any { it() }
            everChanged = everChanged || changed
        } while (changed)

        return everChanged
    }

    private fun constrainFeedbackIndirectlyByAggregatedCountsConstraintComparison(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        constraints: List<Constraint>
    ): Boolean {
        // Given two guesses:
        //  SPITE (1 exact, 0 included)
        //  MIGHT (1 exact, 0 included)
        // Because the position change of I and T did not change an EXACT match to an
        // INCLUDED match, it can be determined that -- if max occurrences is 1 -- neither exist in
        // the secret. This inference is not possible in either AggregatedExact or AggregatedIncluded
        // comparisons.
        // Consider also that there may be other letters with known contributions to EXACT
        // and INCLUDED, so they must be accounted for to produce an overall correct comparison.

        // In general, consider for each character two sets of positions: Exact Positions and
        // "Exclusivity Positions". A position is Exact iff the character must
        // appear there. A position has Exclusivity iff the character _either_ appears there,
        // _or_ does not appear anywhere besides its Exact Positions. Once a nonempty set of Exclusivity
        // Positions is found, attempt to determine whether the character filling ALL such positions
        // is impossible.
        // TODO consider the "conjunction of disjunctions" case, where a particular letter appears
        // more than once in a guess without INCLUDED matches. In that case, neither position fits
        // the definition of an Exclusivity Position, but the two of them _TOGETHER_ do -- the letter
        // must occur in at least one of those two spots, or nowhere in the word.

        val chars = occurrences.keys.toSet()
        val exactCandidateSetIndices = candidates.indices.filter { candidates[it].size == 1 }.toSet()
        val exactPositions = chars.associateWith { char ->
            exactCandidateSetIndices.filter { char in candidates[it] }
        }
        val exclusivityPositions = chars.associateWith { mutableSetOf<Int>() }

        // Each Constraint potentially indicates positions of exclusivity for each char.
        // For instance, a Constraint with Included =0, Exact >0 provides an Exclusivity Position
        // for any char that appears just once. With more care and consideration, Exclusivity Positions
        // can be determined in other cases as well -- for instance, nonzero Included values can
        // be accounted for by specific characters known to be in the wrong spot, leaving all other
        // characters in Exclusivity Positions.
        constraints.forEach { constraint ->
            val word = constraint.candidate
            val charCount = word.toSet().associateWith { char -> word.count { char == it } }

            // a character contributes to "included" if it is known that none of its current
            // positions are correct. Its contribution is the minimum of its positions and
            // its minimum appearances. Any such character should be ignored for exclusivity.
            val includedChars = charCount.keys.filter { char ->
                val allPositionsWrong = word.indices.filter { char == word[it] }
                    .all { char !in candidates[it] }
                val hasPositions = (occurrences[char] ?: 0..0).first > 0
                allPositionsWrong && hasPositions
            }
            val includedCount = includedChars.sumOf { min(charCount[it] ?: 0, (occurrences[it] ?: 0..0).first) }

            val considerIndices = when {
                // if nothing included, any character that occurs just once is in an exclusivity position
                constraint.included == 0 && constraint.exact > 0 -> {
                    word.indices.filter { charCount[word[it]] == 1 }
                }

                // if included is fully explained, any character not considered as "included"
                // that occurs just once is in an exclusivity position
                constraint.included <= includedCount && constraint.exact > 0 -> {
                    word.indices.filter { index ->
                        val char = word[index]
                        char !in includedChars && charCount[char] == 1
                    }
                }

                // TODO there may be other possible scenarios where exclusivity positions
                // can be determined.

                // otherwise, nothing is exclusive. Inferences based on exact = 0
                // are made elsewhere, as they don't require cross-examination between Constraints.
                else -> emptyList()
            }

            // of these, only consider if not already marked EXACT.
            (considerIndices - exactCandidateSetIndices).forEach { index ->
                val char = word[index]
                exclusivityPositions[char]?.add(index)
            }
        }

        // exactPositions and exclusivityPositions represent the definitive set of places
        // where the letter MUST go if possible. exactPositions is known to correct;
        // exclusivityPositions are either ALL also exact, or the letter occurs nowhere besides
        // exactPositions.
        val totalMinimumOccurrences = occurrences.values.sumOf { it.first }
        var changed = false
        chars.filter { char -> !exclusivityPositions[char].isNullOrEmpty() }
            .filter { char ->
                val range = (occurrences[char] ?: 0..0)
                exactPositions[char]!!.size < range.last
            }
            .forEach { char ->
                var impossible = false
                val exact = exactPositions[char] ?: emptyList<Int>()
                val exclusivity = exclusivityPositions[char] ?: emptyList<Int>()

                // is it impossible that the letter appears in ALL positions listed?
                // impossible if that would exceed maximum occurrences for the letter,
                val range = occurrences[char] ?: 0..0
                impossible = impossible || exact.size + exclusivity.size > range.last

                // or if some other letter is known to fill one of those positions,
                impossible = impossible || exclusivity.any { it !in exact && it in exactCandidateSetIndices }

                // or if the minimum occurrences of all letters could not be accommodated.
                impossible = impossible || (totalMinimumOccurrences - range.first) + exact.size + exclusivity.size > length

                if (impossible) {
                    val removeFrom = candidates.indices - exact.toSet()
                    val removed = removeFrom.count { candidates[it].remove(char) }
                    if (removed > 0) changed = true
                }
            }

        if (changed) {
            // candidates and occurrences can influence each other
            constrainCandidatesAndOccurrences(candidates, occurrences)

            // update markup to the best available for each attempted letter
            inferMarkups(candidates, occurrences, markups, includeNonElimination)
        }

        return changed
    }

    private fun constrainFeedbackIndirectlyByAggregatedIncludedCountsConstraintComparison(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        constraints: List<Constraint>
    ): Boolean {
        // any constraint whose INCLUDED count is fully explained by existing Feedback is already
        // represented in that Feedback, and need not be considered separately. To not be
        // considered as such, it must have a higher INCLUDED count than can be explained by
        // the minimum occurrences of each letter present.
        val unrepresentedConstraints = constraints.filter { constraint ->
            val letterCount = constraint.candidate.toSet()
                .associateWith { char -> constraint.candidate.count { it == char } }
            val knownIncludedCount = letterCount.entries.sumOf { (char, count) ->
                val range = occurrences[char] ?: 0..0
                min(count, range.first)
            }

            constraint.exact + constraint.included > knownIncludedCount
        }

        // compare the unrepresented constraints against each other (n^2 / 2), looking for
        // cases where the change it letters perfectly explains the change in INCLUDED count.
        // Do this by first counting the known contributions of letters: letters included
        // up to the minimum, letters included beyond the maximum, and letters shared between
        // both. Then, for the remaining UNKNOWN changes, check for:
        // Number of letters REMOVED is == decrease in INCLUDED count (all letters removed are included,
        //      any added are not)
        // Number of letters ADDED is == increase in INCLUDED count (all letters added are included,
        //      any removed are not)
        var changed = false
        for (i in unrepresentedConstraints.indices) {
            val constraintA = unrepresentedConstraints[i]
            val includedA = constraintA.included + constraintA.exact
            val fullCharsA = constraintA.candidate.toSet()
                .associateWith { char -> constraintA.candidate.count { char == it } }
            for (j in (i + 1) until unrepresentedConstraints.size) {
                val charsA = fullCharsA.toMutableMap()
                val constraintB = unrepresentedConstraints[j]
                val includedB = constraintB.included + constraintB.exact
                val charsB = constraintB.candidate.toSet()
                    .associateWith { char -> constraintB.candidate.count { char == it } }
                    .toMutableMap()

                // remove chars with known contributions to delta (above maximum have 0 contribution,
                // below minimum have 1 contribution).
                var delta = includedB - includedA

                val allChars = listOf(charsA.keys, charsB.keys).flatten().toSet()
                allChars.forEach { char ->
                    val range = occurrences[char] ?: 0..0

                    var keepA = min(charsA[char] ?: 0, range.last)
                    var keepB = min(charsB[char] ?: 0, range.last)

                    // any in A up to minimum contributed -1 to delta
                    val removeFromA = min(keepA, range.first)
                    keepA -= removeFromA
                    delta += removeFromA

                    // any in B up to the minimum contributed +1 to delta
                    val removeFromB = min(keepB, range.first)
                    keepB -= removeFromB
                    delta -= removeFromB

                    // any they still share have a net 0 change.
                    val removeFromBoth = min(keepA, keepB)

                    charsA[char] = keepA - removeFromBoth
                    charsB[char] = keepB - removeFromBoth
                }

                val charsAddedB = charsB.values.sum()
                val charsRemovedA = charsA.values.sum()
                val raiseMinBoundsBy: Map<Char, Int>
                val lowerMaxBoundsBy: Map<Char, Int>
                when {
                    charsAddedB == delta -> {
                        // characters in charsA should be removed from maximum occurrences.
                        // characters in charsB should be added to minimum occurrences.
                        lowerMaxBoundsBy = charsA.filter { it.value > 0 }
                        raiseMinBoundsBy = charsB.filter { it.value > 0 }
                    }
                    charsRemovedA == -delta -> {
                        // characters in charsA should be added to minimum occurrences.
                        // characters in charsB should be removed from maximum occurrences.
                        raiseMinBoundsBy = charsA.filter { it.value > 0 }
                        lowerMaxBoundsBy = charsB.filter { it.value > 0 }
                    }
                    else -> {
                        raiseMinBoundsBy = emptyMap()
                        lowerMaxBoundsBy = emptyMap()
                    }
                }

                // now adjust bounds
                allChars.forEach {  char ->
                    val raiseMin = raiseMinBoundsBy[char] ?: 0
                    val lowerMax = lowerMaxBoundsBy[char] ?: 0
                    if (raiseMin != 0 || lowerMax != 0) {
                        val range = occurrences[char] ?: 0..0
                        val adjusted = range.bound(
                            minimum = setOf(range.first + raiseMin),
                            maximum = setOf(range.last - lowerMax)
                        )
                        if (range.first != adjusted.first || range.last != adjusted.last) {
                            changed = true
                            occurrences[char] = adjusted
                        }
                    }
                }
            }
        }

        if (changed) {
            // candidates and occurrences can influence each other
            constrainCandidatesAndOccurrences(candidates, occurrences)

            // update markup to the best available for each attempted letter
            inferMarkups(candidates, occurrences, markups, includeNonElimination)
        }

        return changed
    }

    private fun constrainFeedbackIndirectlyByAggregatedExactCountsConstraintComparison(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean,
        constraints: List<Constraint>
    ): Boolean {
        // any constraint whose EXACT count is fully explained by existing Feedback is already
        // represented in that Feedback, and need not be considered separately. To not be
        // considered as such, it must have nonzero EXACT whose positions are not known.
        val unrepresentedConstraints = constraints.filter { constraint ->
            val knownExactCount = candidates.indices.count {
                val char = constraint.candidate[it]
                val set = candidates[it]
                char in set && set.size == 1
            }
            constraint.exact > knownExactCount
        }

        // compare the unrepresentedConstraints against each other (n^2 / 2), looking for cases where
        // the change in letters perfectly explains the change in EXACT count. Do this by
        // first counting the contribution of known changes in-place (removing a non-candidate
        // has change 0, adding or removing an exact match has change +/-1, etc.). Then, for
        // the remaining UNKNOWN changes, check for:
        // Change in EXACT is == number of in-place replacements (all chars are EXACT matches)
        // One in-place change produces no change in EXACT count (both are NO)
        var changed = false
        for (i in unrepresentedConstraints.indices) {
            val constraintA = unrepresentedConstraints[i]
            for (j in (i + 1) until unrepresentedConstraints.size) {
                val constraintB = unrepresentedConstraints[j]

                var delta = constraintB.exact - constraintA.exact
                val indices = constraintA.candidate.indices.filter {
                    val set = candidates[it]
                    val charA = constraintA.candidate[it]
                    val charB = constraintB.candidate[it]
                    when {
                        // same letter; no delta
                        charA == charB -> false
                        charA in set && set.size == 1 -> {
                            // A is exact match; it contributed -1 to delta.
                            delta += 1
                            false
                        }
                        charB in set && set.size == 1 -> {
                            // B is exact match; it contributed +1 to delta.
                            delta -= 1
                            false
                        }
                        // neither is a match; no delta
                        charA !in set && charB !in set -> false
                        else -> true
                    }
                }

                // check for inference conditions
                when {
                    indices.isEmpty() -> {
                        // ignore; no characters to consider
                    }
                    indices.size == delta -> {
                        // all remaining B chars are EXACT
                        indices.forEach { candidates[it] = mutableSetOf(constraintB.candidate[it]) }
                        changed = true
                    }
                    indices.size == -delta -> {
                        // all remaining A chars are EXACT
                        indices.forEach { candidates[it] = mutableSetOf(constraintA.candidate[it]) }
                        changed = true
                    }
                    indices.size == 1 && delta == 0 -> {
                        // both A and B have the wrong character
                        indices.forEach { index ->
                            val set = candidates[index]
                            set.remove(constraintA.candidate[index])
                            set.remove(constraintB.candidate[index])
                        }
                        changed = true
                    }
                }
            }
        }

        if (changed) {
            // candidates and occurrences can influence each other
            constrainCandidatesAndOccurrences(candidates, occurrences)

            // update markup to the best available for each attempted letter
            inferMarkups(candidates, occurrences, markups, includeNonElimination)
        }

        return changed
    }
    
    private fun inferMarkups(
        candidates: MutableList<MutableSet<Char>>,
        occurrences: MutableMap<Char, IntRange>,
        markups: MutableMap<Char, Constraint.MarkupType?>,
        includeNonElimination: Boolean
    ): Boolean {
        // update markup to the best available for each attempted letter
        var changed = false
        occurrences.keys.forEach { char ->
            val range = occurrences[char] ?: 0..0
            val cMarkup = markups[char]
            val zMarkup = when {
                candidates.any { it.size == 1 && char in it } -> Constraint.MarkupType.EXACT
                range.first > 0 -> Constraint.MarkupType.INCLUDED
                range.last == 0 -> Constraint.MarkupType.NO
                else -> null
            }
            val nMarkup = listOf(cMarkup, zMarkup).maxByOrNull { it?.value() ?: -1 }
            if (nMarkup != cMarkup && (includeNonElimination || nMarkup == Constraint.MarkupType.NO)) {
                markups[char] = nMarkup
                changed = true
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

    private fun asFeedback(
        candidates: List<Set<Char>>,
        occurrences: Map<Char, IntRange>,
        markups:  Map<Char, Constraint.MarkupType?>
    ): Feedback {
        return Feedback(
            candidates = candidates.map { it.toSet() }.toList(),
            occurrences = occurrences.toMap(),
            markup = markups.toMap()
        )
    }
}