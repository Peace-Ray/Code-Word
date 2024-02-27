package com.peaceray.codeword.play.setup
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.random.ConsistentRandom

data class ConsoleGameSettings(
    val vocabulary: Vocabulary? = null,
    val difficulty: Difficulty? = null,
    val players: Players = Players()
) {
    data class Vocabulary (
        val words: Boolean = true,
        val length: Int = 5,
        val characterCount: Int = if (words) 26 else 6,
        val repetitions: Boolean = true
    ) {
        companion object {
            fun random(seed: Long? = null): Vocabulary {
                val r = ConsistentRandom(seed ?: System.currentTimeMillis())
                val words = r.nextBoolean()
                val length = (3..8).random(r)
                val repetitions = r.nextBoolean()
                val characterCount = when {
                    words -> 26
                    !repetitions -> (length..12).random(r)
                    else -> (4..12).random(r)
                }
                return Vocabulary(words, length, characterCount, repetitions)
            }
        }
    }

    data class Difficulty (
        val hard: Boolean = false,
        val feedbackPolicy: ConstraintPolicy = ConstraintPolicy.POSITIVE,
        val letterFeedback: LetterFeedback = LetterFeedback.NONE
    ) {

        enum class LetterFeedback {
            NONE,
            DIRECT,
            DIRECT_AND_INFERRED;
        }

        constructor(vocabulary: Vocabulary?): this(
            hard = false,
            feedbackPolicy = if (vocabulary?.words == true) ConstraintPolicy.POSITIVE else ConstraintPolicy.AGGREGATED,
            letterFeedback = LetterFeedback.NONE
        )

        companion object {
            fun random(vocabulary: Vocabulary, seed: Long? = null): Difficulty {
                val offset = (
                        vocabulary.length
                        + (if (vocabulary.words) 1 else 0) * 10
                        + (if (vocabulary.repetitions) 1 else 0) * 20
                        + (if (vocabulary.characterCount == 1) 1 else 0) * 40
                )

                val r = ConsistentRandom(offset + (seed ?: System.currentTimeMillis()))
                val feedbackPolicy = listOf(
                    ConstraintPolicy.POSITIVE,
                    ConstraintPolicy.AGGREGATED,
                    ConstraintPolicy.AGGREGATED_EXACT,
                    ConstraintPolicy.AGGREGATED_INCLUDED
                ).random(r)
                val hard = if (feedbackPolicy == ConstraintPolicy.POSITIVE) r.nextBoolean() else false
                val letterFeedback = LetterFeedback.entries.random(r)

                return Difficulty(hard, feedbackPolicy, letterFeedback)
            }
        }
    }

    data class Players (
        val guesser: Guesser = Guesser.PLAYER,
        val keeper: Keeper = Keeper.HONEST_BOT
    ) {
        enum class Guesser {
            PLAYER,
            KNUTH_MINIMAX,
            DECISION_TREE,
            REALISTIC_DECISION_TREE,
            RANDOM_DRAW;
        }

        enum class Keeper {
            PLAYER_AUTOMATIC,
            PLAYER_MANUAL,
            HONEST_BOT,
            CHEATING_BOT;
        }
    }

    fun with(vocabulary: Vocabulary?): ConsoleGameSettings {
        // may change difficulty
        val difficulty: Difficulty? = if (this.vocabulary == null || vocabulary == null) this.difficulty else when {
            this.difficulty == null -> null

            // same style of secret, possibly changing length or character counts
            this.vocabulary.words == vocabulary.words && this.vocabulary.repetitions == vocabulary.repetitions ->
                this.difficulty

            // went from words to non-words, or vice-versa
            this.vocabulary.words != vocabulary.words -> Difficulty(vocabulary)

            // repetitions: YES -> NO
            this.vocabulary.repetitions && !vocabulary.repetitions -> {
                if (this.difficulty == null) null else {
                    val feedbackPolicy = if (!this.difficulty.feedbackPolicy.isByLetter()) {
                        this.difficulty.feedbackPolicy
                    } else {
                        ConstraintPolicy.AGGREGATED_INCLUDED
                    }

                    val letterFeedback = if (this.difficulty.letterFeedback == Difficulty.LetterFeedback.DIRECT) {
                        Difficulty.LetterFeedback.DIRECT_AND_INFERRED
                    } else {
                        this.difficulty.letterFeedback
                    }

                    Difficulty(this.difficulty.hard, feedbackPolicy, letterFeedback)
                }
            }

            // repetitions: NO -> YES
            else -> {

                val letterFeedback = when {
                    this.difficulty.feedbackPolicy.isByLetter() -> Difficulty.LetterFeedback.DIRECT
                    else -> this.difficulty.letterFeedback
                }

                Difficulty(this.difficulty.hard, this.difficulty.feedbackPolicy, letterFeedback)
            }
        }

        return ConsoleGameSettings(vocabulary, difficulty, players)
    }

    fun with(difficulty: Difficulty?): ConsoleGameSettings = ConsoleGameSettings(vocabulary, difficulty, players)

    fun with(players: Players): ConsoleGameSettings = ConsoleGameSettings(vocabulary, difficulty, players)
}