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
    }

    val exact = markup.count { it == MarkupType.EXACT }
    val included = markup.count { it == MarkupType.INCLUDED }

    val correct = exact == candidate.length

    companion object {
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
                ConstraintPolicy.POSITIVE, ConstraintPolicy.ALL -> markup.map { it.asKey() }.joinToString("")
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
                ConstraintPolicy.POSITIVE, ConstraintPolicy.ALL -> markupKeys.joinToString("")
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
     */
    fun allows(guess: String, policy: ConstraintPolicy): Boolean {
        return when (policy) {
            ConstraintPolicy.IGNORE -> true  // always allowed
            ConstraintPolicy.AGGREGATED -> {
                val (matchPairs, unmatchedPairs) = candidate.zip(guess).partition { it.first == it.second }
                val (candidateUnmatched, guessUnmatched) = unmatchedPairs.unzip()
                val remainingUnmatched = guessUnmatched.toMutableList()

                // check that this matches the constraint's exact and included counts
                exact == matchPairs.size
                        && included == candidateUnmatched.count { remainingUnmatched.remove(it) }
            }
            else -> {
                val zipped = candidate.zip(guess)

                // find an exact match that is not satisfied
                if (zipped.filterIndexed { index, _ -> markup[index] == Constraint.MarkupType.EXACT }
                        .any { it.first != it.second }
                ) {
                    return false
                }

                val available = guess.filterIndexed { index, _ -> markup[index] != MarkupType.EXACT }
                    .toMutableList()

                // all exact matches ok; look for unsatisfied value matches
                candidate
                    .filterIndexed { index, _ -> markup[index] == MarkupType.INCLUDED }
                    .forEach {
                        if (it !in available) return false
                        available.remove(it)
                    }

                if (policy == ConstraintPolicy.ALL) {
                    // non-included letters must not appear in the remaining string
                    candidate
                        .filterIndexed { index, _ -> markup[index] == MarkupType.NO }
                        .forEach {
                            if (it in available) return false
                        }
                }

                // allow
                true
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
}