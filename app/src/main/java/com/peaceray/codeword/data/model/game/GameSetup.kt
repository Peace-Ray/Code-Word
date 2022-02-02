package com.peaceray.codeword.data.model.game

import android.os.Parcelable
import com.peaceray.codeword.game.data.ConstraintPolicy
import kotlinx.parcelize.Parcelize

/**
 * Parameters for a Game setup. Independent of actual player moves. Note that this describes,
 * very broadly, the type of game being played. It does NOT represent the implementation specifics
 * of those games; e.g. for a cpu "guesser", algorithm and difficulty settings are not pertinent
 * to the GameSession description. Essentially these are things that, at least vaguely, are
 * relevant to the UI layer: size of the game board, whether the player is entering guesses or
 * evaluations, etc.
 *
 * Fully describes the initial state and player configuration for a Game in enums, primitives, etc.
 * e.g. without any Validator functions or player implementations. Includes:
 *
 * The size of the game board: code length and number of rounds (0 for forever game)
 * Evaluation type: aggregated code breaking or per-character annotation.
 * Code vocabulary: enumerated char sequences, vocabulary list
 * Candidate constraints: how constraints are applied to new guesses
 * Feedback constraints: how evaluations represented to users
 * Evaluator player type: human, honest evaluator, cheating evaluator.
 * Guesser player type: human, solver
 */
@Parcelize
data class GameSetup(
    val board: Board,
    val evaluation: Evaluation,
    val vocabulary: Vocabulary,
    val solver: Solver,
    val evaluator: Evaluator
): Parcelable {
    @Parcelize data class Board(val letters: Int, val rounds: Int): Parcelable
    @Parcelize data class Evaluation(val type: ConstraintPolicy, val enforced: ConstraintPolicy = ConstraintPolicy.IGNORE): Parcelable
    @Parcelize data class Vocabulary(val type: VocabularyType, val characters: Int = 0): Parcelable {
        enum class VocabularyType { LIST, ENUMERATED }
    }
    enum class Solver { PLAYER, BOT }
    enum class Evaluator { PLAYER, HONEST, CHEATER }

    companion object {
        fun forWordPuzzle(honest: Boolean = true, hard: Boolean = false) = GameSetup(
            board = Board(5, if (honest) 6 else 0),
            evaluation = Evaluation(ConstraintPolicy.ALL, if (hard) ConstraintPolicy.POSITIVE else ConstraintPolicy.IGNORE),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.LIST),
            solver = Solver.PLAYER,
            evaluator = if (honest) Evaluator.HONEST else Evaluator.CHEATER
        )

        fun forWordEvaluation(hard: Boolean = false, rounds: Int = 6)  = GameSetup(
            board = Board(5, rounds),
            evaluation = Evaluation(ConstraintPolicy.ALL, if (hard) ConstraintPolicy.POSITIVE else ConstraintPolicy.IGNORE),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.LIST),
            solver = Solver.BOT,
            evaluator = Evaluator.PLAYER
        )

        fun forWordDemo(honest: Boolean = true, hard: Boolean = false) = GameSetup(
            board = Board(5, if (honest) 6 else 0),
            evaluation = Evaluation(ConstraintPolicy.ALL, if (hard) ConstraintPolicy.POSITIVE else ConstraintPolicy.IGNORE),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.LIST),
            solver = Solver.BOT,
            evaluator = if (honest) Evaluator.HONEST else Evaluator.CHEATER
        )

        fun forCodePuzzle(
            length: Int = 4,
            rounds: Int = 10,
            characters: Int = 6,
            honest: Boolean = true
        ) = GameSetup(
            board = Board(length, rounds),
            evaluation = Evaluation(ConstraintPolicy.AGGREGATED),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.ENUMERATED, characters),
            solver = Solver.PLAYER,
            evaluator = if (honest) Evaluator.HONEST else Evaluator.CHEATER
        )

        fun forCodeEvaluation(
            length: Int = 4,
            rounds: Int = 10,
            characters: Int = 6
        ) = GameSetup(
            board = Board(length, rounds),
            evaluation = Evaluation(ConstraintPolicy.AGGREGATED),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.ENUMERATED, characters),
            solver = Solver.BOT,
            evaluator = Evaluator.PLAYER
        )

        fun forCodeDemo(
            length: Int = 4,
            rounds: Int = 10,
            characters: Int = 6,
            honest: Boolean = true
        ) = GameSetup(
            board = Board(length, rounds),
            evaluation = Evaluation(ConstraintPolicy.AGGREGATED),
            vocabulary = Vocabulary(Vocabulary.VocabularyType.ENUMERATED, characters),
            solver = Solver.BOT,
            evaluator = if (honest) Evaluator.HONEST else Evaluator.CHEATER
        )
    }

}