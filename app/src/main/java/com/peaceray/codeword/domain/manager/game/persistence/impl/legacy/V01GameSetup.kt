package com.peaceray.codeword.domain.manager.game.persistence.impl.legacy

import android.os.Parcelable
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.game.data.ConstraintPolicy
import kotlinx.parcelize.Parcelize
import kotlin.IllegalArgumentException
import kotlin.random.Random

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
data class V01GameSetup(
    val board: Board,
    val evaluation: Evaluation,
    val vocabulary: Vocabulary,
    val solver: Solver,
    val evaluator: Evaluator,
    val randomSeed: Long,
    val daily: Boolean = false,
    val version: Int
): Parcelable {
    @Parcelize data class Board(val rounds: Int): Parcelable {
        init {
            if (rounds < 0) throw IllegalArgumentException("rounds must be non-negative")
        }
    }
    @Parcelize data class Evaluation(val type: ConstraintPolicy, val enforced: ConstraintPolicy = ConstraintPolicy.IGNORE): Parcelable
    @Parcelize data class Vocabulary(val language: CodeLanguage, val type: VocabularyType, val length: Int, val characters: Int, val secret: String? = null): Parcelable {
        enum class VocabularyType { LIST, ENUMERATED }
        init {
            if (length <= 0) throw IllegalArgumentException("letters must be positive")
            if (characters <= 0) throw IllegalArgumentException("characters must be positive")
            if (secret != null && secret.length != length) throw IllegalArgumentException("secret length must match vocabulary length")
        }
    }
    enum class Solver { PLAYER, BOT }
    enum class Evaluator { PLAYER, HONEST, CHEATER }

    fun with(
        board: Board? = null,
        evaluation: Evaluation? = null,
        vocabulary: Vocabulary? = null,
        solver: Solver? = null,
        evaluator: Evaluator? = null,
        daily: Boolean? = null,
        randomSeed: Long? = null,
        randomized: Boolean = false,
        version: Int? = null
    ) = V01GameSetup(
        board = board ?: this.board,
        evaluation = evaluation ?: this.evaluation,
        vocabulary = vocabulary ?: this.vocabulary,
        solver = solver ?: this.solver,
        evaluator = evaluator ?: this.evaluator,
        daily = daily ?: this.daily,
        randomSeed = if (randomized) createSeed() else randomSeed ?: this.randomSeed,
        version = version ?: this.version
    )

    companion object {
        fun createSeed() = Random.nextLong(Int.MAX_VALUE.toLong())
    }

}