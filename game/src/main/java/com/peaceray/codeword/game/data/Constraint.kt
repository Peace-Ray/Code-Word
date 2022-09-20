package com.peaceray.codeword.game.data

/**
 * A constraint, based on an example code (e.g. a candidate presented by the guesser), that the
 * correct code must fit.
 *
 * A candidate code is a string of markup (e.g. letters); each key is associated with a label
 * indicating its usage in the true code. In some implementations, this information is presented
 * directly to the guesser (marking each letter according to label); in others this information
 * is aggregated with guessers only knowing the *number* of exact and included characters.
 */
data class Constraint private constructor(val candidate: String, val markup: List<MarkupType>) {

    /**
     * A label marking a key (letter) in the candidate code, indicating its usage in
     * the unknown "true" code.
     */
    enum class MarkupType {
        /**
         * This key occurs in this exact position.
         */
        EXACT {
            override fun asKey() = 'E'
        },

        /**
         * This key occurs in the correct code, but not in this position.
         */
        INCLUDED {
            override fun asKey() = 'I'
        },

        /**
         * This key does not occur in the correct code.
         */
        NO {
            override fun asKey() = 'N'
        };

        abstract fun asKey(): Char

        companion object {
            fun fromKey(key: Char) = when(key) {
                'E' -> EXACT
                'I' -> INCLUDED
                'N' -> NO
                else -> throw IllegalArgumentException("No such MarkupType: $key")
            }
        }
    }

    /**
     * Describes a Violation of a Constraint by a guess or other candidate code.
     *
     * @param constraint The Constraint being violated
     * @param candidate The candidate string which violates the Constraint
     * @param markup The MarkupType being violated. e.g. for [MarkupType.EXACT], the letter
     * does not appear in that location of the Candidate.
     * @param character The character on which the Markup is applied, if applicable.
     * @param position The position in the Constraint at which the Markup is applied, if applicable.
     * @param candidatePosition The position in the candidate at which the violating character is found, if any.
     */
    data class Violation(
        val constraint: Constraint,
        val candidate: String,
        val markup: MarkupType,
        val position: Int? = null,
        val character: Char? = null,
        val candidatePosition: Int? = null,
        val candidateCharacter: Char? = null
    ) {
        constructor(
            constraint: Constraint,
            candidate: String,
            position: Int,
            candidatePosition: Int? = null
        ): this(
            constraint,
            candidate,
            constraint.markup[position],
            position,
            constraint.candidate[position],
            candidatePosition,
            if (candidatePosition == null) null else candidate[candidatePosition]
        )
    }

    val exact = markup.count { it == MarkupType.EXACT }
    val included = markup.count { it == MarkupType.INCLUDED }

    val correct = exact == candidate.length

    companion object {
        /**
         * Construct a Constraint based on the string, as output from [toString]
         */
        fun fromString(string: String): Constraint {
            val parts = string.split(",")
            val markup = parts[1].map { MarkupType.fromKey(it) }
            return create(parts[0], markup)
        }

        /**
         * Construct a Constraint based on a candidate guess and its key-by-key markup.
         *
         * @param guess The candidate guess
         * @param markup Letter-by-letter markup for the candidate (must match [guess.length])
         */
        fun create(guess: String, markup: List<MarkupType>): Constraint {
            if (guess.length != markup.size) {
                throw IllegalArgumentException("Guess and matches length must be equal")
            }

            return Constraint(guess, markup.toList())
        }

        /**
         * Construct a Constraint based on a candidate guess and the underlying "true" secret.
         *
         * @param guess The candidate guess
         * @param secret The underlying "true" code
         */
        fun create(guess: String, secret: String): Constraint {
            if (guess.length != secret.length) {
                throw IllegalArgumentException("Guess $guess and secret $secret must have equal length")
            }

            // start with all NOs
            val markup = MutableList(guess.length) { MarkupType.NO }

            // populate EXACT matches
            guess.zip(secret)
                .forEachIndexed { index, pair ->
                    if (pair.first == pair.second) {
                        markup[index] = MarkupType.EXACT
                    }
                }

            // populate VALUE matches
            val unusedSecret = secret.filterIndexed { index, _ -> markup[index] == MarkupType.NO }
                .toMutableList()
            guess.forEachIndexed { index, c ->
                if (markup[index] == MarkupType.NO && c in unusedSecret) {
                    markup[index] = MarkupType.INCLUDED
                    unusedSecret.remove(c)
                }
            }

            return Constraint(guess, markup.toList())
        }

        fun asKey(guess: String, markup: List<MarkupType>, policy: ConstraintPolicy): String {
            if (guess.length != markup.size) {
                throw IllegalArgumentException("Guess $guess and markup must have equal length")
            }

            return when(policy) {
                ConstraintPolicy.IGNORE -> if (markup.all { it == MarkupType.EXACT }) "CORRECT" else "INCORRECT"
                ConstraintPolicy.AGGREGATED -> "${markup.count { it == MarkupType.EXACT }},${markup.count { it == MarkupType.INCLUDED }}"
                ConstraintPolicy.POSITIVE, ConstraintPolicy.ALL, ConstraintPolicy.PERFECT -> markup.map { it.asKey() }.joinToString("")
            }
        }

        fun asKey(guess: String, secret: String, policy: ConstraintPolicy): String {
            if (guess.length != secret.length) {
                throw IllegalArgumentException("Guess $guess and secret $secret must have equal length")
            }

            // 2-pass. In the first non-exact secret characters. In the second assign markup keys.
            val nonExactSecret = mutableListOf<Char>()
            secret.forEachIndexed { index, c -> if (guess[index] != c) nonExactSecret.add(c) }

            val markupKeys = guess.mapIndexed { index, c ->
                when(c) {
                    secret[index] -> MarkupType.EXACT.asKey()
                    in nonExactSecret -> {
                        nonExactSecret.remove(c)
                        MarkupType.INCLUDED.asKey()
                    }
                    else -> MarkupType.NO.asKey()
                }
            }

            return when(policy) {
                ConstraintPolicy.IGNORE -> if (guess == secret) "CORRECT" else "INCORRECT"
                ConstraintPolicy.AGGREGATED -> {
                    val eKey = MarkupType.EXACT.asKey()
                    val iKey = MarkupType.INCLUDED.asKey()
                    "${markupKeys.count { it == eKey }},${markupKeys.count { it == iKey }}"
                }
                ConstraintPolicy.POSITIVE, ConstraintPolicy.ALL, ConstraintPolicy.PERFECT -> markupKeys.joinToString("")
            }
        }
    }

    /**
     * Returns whether the provided guess is consistent with this Constraint; i.e. whether it is not
     * eliminated by it according to the specified policy. Note that for games based only on
     * [exact] and [included] counts, where the specific letters marked as such are not revealed to
     * the guesser, only a policy of [ConstraintPolicy.IGNORE] makes sense.
     *
     * @param guess The guess to check against this constraint
     * @param policy The policy to apply.
     * @param partial A partial guess is provided. If 'true', will allow guesses that hypothetically
     * could be _completed_ to match the constraint.
     */
    fun allows(guess: String, policy: ConstraintPolicy, partial: Boolean = false): Boolean {
        // length check
        val slack = candidate.length - guess.length
        if (slack < 0 || (!partial && slack > 0)) return false

        return when (policy) {
            ConstraintPolicy.IGNORE -> true  // always allowed
            ConstraintPolicy.AGGREGATED -> {
                // a guess is consistent with an AGGREGATED constraint if the candidate
                // would receive that markup if attempted as a guess for the provided guess string.
                val (matchPairs, unmatchedPairs) = candidate.zip(guess).partition { it.first == it.second }
                val (candidateUnmatched, guessUnmatched) = unmatchedPairs.unzip()
                val remainingUnmatched = (candidateUnmatched + candidate.slice(guess.length until candidate.length).toList()).toMutableList()

                // check that this matches the constraint's exact and included counts
                val exact = matchPairs.size
                val included = guessUnmatched.count { remainingUnmatched.remove(it) }

                if (!partial) exact == this.exact && included == this.included else {
                    val slackRequired = (this.exact - exact) + (this.included - included)
                    exact <= this.exact && included <= this.included && slackRequired <= slack
                }
            }
            else -> {
                val zipped = candidate.zip(guess)

                // find an exact match that is not satisfied (among guess characters)
                if (zipped.filterIndexed { index, _ -> markup[index] == MarkupType.EXACT }.any { it.first != it.second }) {
                    return false
                }

                // count exact match characters yet-to-come in partial guess
                val unsatisfiedExact = candidate.indices.count { it >= guess.length && markup[it] == MarkupType.EXACT }

                // find an included match that appears in the same spot (disallowed by PERFECT)
                if (policy == ConstraintPolicy.PERFECT) {
                    if (zipped.filterIndexed { index, _ -> markup[index] == MarkupType.INCLUDED }.any { it.first == it.second }) {
                        return false
                    }
                }

                val available = guess.filterIndexed { index, _ -> markup[index] != MarkupType.EXACT }
                    .toMutableList()

                // all exact matches ok; look for unsatisfied value matches
                var unsatisfiedIncluded = 0
                candidate
                    .filterIndexed { index, _ -> markup[index] == MarkupType.INCLUDED }
                    .forEach {
                        if (it !in available) unsatisfiedIncluded++
                        available.remove(it)
                    }

                if (policy == ConstraintPolicy.ALL || policy == ConstraintPolicy.PERFECT) {
                    // non-included letters must not appear in the remaining string
                    candidate
                        .filterIndexed { index, _ -> markup[index] == MarkupType.NO }
                        .forEach {
                            if (it in available) return false
                        }
                }

                if (!partial) unsatisfiedIncluded == 0 else {
                    unsatisfiedIncluded + unsatisfiedExact <= slack
                }
            }
        }
    }

    /**
     * Returns the violations found when comparing the provided guess against this constraint
     * according to the specified policy.
     *
     * whether the provided guess is consistent with this Constraint; i.e. whether it is not
     * eliminated by it according to the specified policy. Note that for games based only on
     * [exact] and [included] counts, where the specific letters marked as such are not revealed to
     * the guesser, only a policy of [ConstraintPolicy.IGNORE] makes sense, but
     * [ConstraintPolicy.AGGREGATED] will provide "position-less" responses.
     *
     * @param guess The guess to check against this constraint
     * @param policy The policy to apply.
     * @return A List of pairs, with each item showing the [MarkupType] and string-position
     * of a constraint character that is not matched by the guess.
     */
    fun violations(guess: String, policy: ConstraintPolicy): List<Violation> {
        val list = mutableListOf<Violation>()
        return when (policy) {
            ConstraintPolicy.IGNORE -> list.toList()  // always allowed
            ConstraintPolicy.AGGREGATED -> {
                val (matchPairs, unmatchedPairs) = candidate.zip(guess).partition { it.first == it.second }
                val (candidateUnmatched, guessUnmatched) = unmatchedPairs.unzip()
                val remainingUnmatched = guessUnmatched.toMutableList()

                // check that this matches the constraint's exact and included counts
                val extraMatches = matchPairs.size - exact
                val includedAndExtra = candidateUnmatched.count { remainingUnmatched.remove(it) } + extraMatches

                if (extraMatches < 0) list.add(Violation(this, guess, MarkupType.EXACT))
                if (included > includedAndExtra) list.add(Violation(this, guess, MarkupType.INCLUDED))
                list.toList()
            }
            else -> {
                val zipped = candidate.zip(guess)

                // find exact matches that are not satisfied
                zipped.forEachIndexed { index, pair ->
                    if (markup[index] == MarkupType.EXACT && pair.first != pair.second) {
                        list.add(Violation(this, guess, index, index))
                    }
                }

                // in PERFECT policy, INCLUDED characters must not occur in the same spot.
                if (policy == ConstraintPolicy.PERFECT) {
                    zipped.forEachIndexed { index, pair ->
                        if (markup[index] == MarkupType.INCLUDED && pair.first == pair.second) {
                            list.add(Violation(this, guess, index, index))
                        }
                    }
                }

                // all INCLUDED characters must be accounted for; exclude valid EXACT matches.
                val available = guess.filterIndexed { index, c ->
                    markup[index] != MarkupType.EXACT || c != candidate[index]
                }.toMutableList()

                candidate.forEachIndexed { index, c ->
                    if (markup[index] == MarkupType.INCLUDED) {
                        if (c in available) {
                            available.remove(c)
                        } else {
                            list.add(Violation(this, guess, index))
                        }
                    }
                }

                if (policy == ConstraintPolicy.ALL || policy == ConstraintPolicy.PERFECT) {
                    // non-included letters must not appear in the remaining string
                    val remaining = guess.toMutableList()
                    val indices = MutableList(remaining.size) { it }
                    candidate.forEachIndexed { index, c ->
                        if (markup[index] != MarkupType.NO) {
                            val index = remaining.indexOf(c)
                            if (index >= 0) {
                                remaining.removeAt(index)
                                indices.removeAt(index)
                            }
                        }
                    }

                    candidate.forEachIndexed { index, c ->
                        if (markup[index] == MarkupType.NO && c in remaining) {
                            val origIndex = indices[remaining.indexOf(c)]
                            list.add(Violation(this, guess, index, origIndex))
                            val indexNow = remaining.indexOf(c)
                            remaining.removeAt(indexNow)
                            indices.removeAt(indexNow)
                        }
                    }
                }

                list.toList()
            }
        }
    }

    /**
     * Represents the constraint as a key string determined by [policy]; if a different
     * constraint matches the string exactly, the two describe the same "elimination set"
     * (assuming they are based on the same guess). Used as an intermediate value when scoring
     * possible guess candidates; not terribly useful generally.
     */
    fun asKey(policy: ConstraintPolicy): String {
        return asKey(candidate, markup, policy)
    }

    override fun toString(): String {
        return "$candidate,${markup.map { it.asKey() }.joinToString("")}"
    }
}