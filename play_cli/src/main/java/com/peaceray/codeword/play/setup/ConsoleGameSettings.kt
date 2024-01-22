package com.peaceray.codeword.play.setup
import com.peaceray.codeword.game.feedback.ConstraintFeedbackPolicy
import java.util.stream.Collectors.toSet

data class ConsoleGameSettings(
    val vocabulary: Vocabulary = Vocabulary(),
    val difficulty: Difficulty = Difficulty(),
    val players: Players = Players()
) {
    data class Vocabulary (
        val words: Boolean = true,
        val length: Int = 5,
        val characterCount: Int = if (words) 26 else 6,
        val repetitions: Boolean = true
    ) {
        init {
            if (!words && !repetitions) {
                throw IllegalArgumentException("Can't currently support enumerated codes w/o repetitions")
            }
        }
    }

    data class Difficulty (
        val hard: Boolean = false,
        val constraintFeedbackPolicy: ConstraintFeedbackPolicy = ConstraintFeedbackPolicy.CHARACTER_MARKUP,
        val letterFeedback: LetterFeedback = LetterFeedback.NONE
    ) {

        enum class LetterFeedback {
            NONE,
            DIRECT,
            DIRECT_AND_INFERRED;
        }

        constructor(vocabulary: Vocabulary): this(
            hard = false,
            constraintFeedbackPolicy = if (vocabulary.words) ConstraintFeedbackPolicy.CHARACTER_MARKUP else ConstraintFeedbackPolicy.AGGREGATED_MARKUP,
            letterFeedback = LetterFeedback.NONE
        )
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

    fun with(vocabulary: Vocabulary): ConsoleGameSettings {
        // may change difficulty
        val difficulty: Difficulty = when {
            // same style of secret, possibly changing length or character counts
            this.vocabulary.words == vocabulary.words && this.vocabulary.repetitions == vocabulary.repetitions ->
                this.difficulty

            // went from words to non-words, or vice-versa
            this.vocabulary.words != vocabulary.words ->
                Difficulty(vocabulary)

            // repetitions: YES -> NO
            this.vocabulary.repetitions && !vocabulary.repetitions -> {
                val feedbackPolicy = if (this.difficulty.constraintFeedbackPolicy != ConstraintFeedbackPolicy.CHARACTER_MARKUP) {
                    this.difficulty.constraintFeedbackPolicy
                } else {
                    ConstraintFeedbackPolicy.COUNT_INCLUDED
                }

                val letterFeedback = if (this.difficulty.letterFeedback == Difficulty.LetterFeedback.DIRECT) {
                    Difficulty.LetterFeedback.DIRECT_AND_INFERRED
                } else {
                    this.difficulty.letterFeedback
                }

                Difficulty(this.difficulty.hard, feedbackPolicy, letterFeedback)
            }

            // repetitions: NO -> YES
            else -> {
                val letterFeedback = if (this.difficulty.constraintFeedbackPolicy == ConstraintFeedbackPolicy.CHARACTER_MARKUP) {
                    Difficulty.LetterFeedback.DIRECT
                } else {
                    this.difficulty.letterFeedback
                }

                Difficulty(this.difficulty.hard, this.difficulty.constraintFeedbackPolicy, letterFeedback)
            }
        }

        return ConsoleGameSettings(vocabulary, difficulty, players)
    }

    fun with(difficulty: Difficulty): ConsoleGameSettings = ConsoleGameSettings(vocabulary, difficulty, players)

    fun with(players: Players): ConsoleGameSettings = ConsoleGameSettings(vocabulary, difficulty, players)
}