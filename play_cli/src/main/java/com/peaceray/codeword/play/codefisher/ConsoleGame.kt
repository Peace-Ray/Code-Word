package com.peaceray.codeword.play.codefisher;

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.game.bot.modules.generation.CodeCollapsedEnumerationGenerator
import com.peaceray.codeword.game.bot.modules.generation.CodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.selection.MaximumScoreSelector
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.validators.AlphabetValidator
import com.peaceray.codeword.game.data.Settings as CodeGameSettings

val ALPHABET = 'A'..'F'
val ALPHABET_LETTERS = listOf('A', 'B', 'C', 'D', 'E', 'F')
const val LENGTH = 5
const val ROUNDS = 10

fun main() {
    // random guess
    val secret = generateSecret()

    // create game
    val validator = AlphabetValidator(ALPHABET)
    val game = Game(CodeGameSettings(LENGTH, ROUNDS), validator)

    // create solver
    // val solver = KnuthMiniMaxSolver(listOf("").extendCodes(LENGTH))
    // val solver = KnuthMiniStochasticSolver(listOf("").extendCodes(LENGTH))
    val solver = ModularSolver(
        CodeCollapsedEnumerationGenerator(
            ALPHABET_LETTERS,
            LENGTH,
            ConstraintPolicy.IGNORE,
            ConstraintPolicy.AGGREGATED,
            shuffle = true,
            truncateAtProduct = 1000000
        ),
        InformationGainScorer(ConstraintPolicy.AGGREGATED),
        // KnuthMinimumScorer(ConstraintPolicy.AGGREGATED),
        MaximumScoreSelector()
    )

    // first operation takes a while...
    print("Getting started")
    val startTime = System.currentTimeMillis()

    // loop
    while (!game.over) {
        // attempt a guess
        val guess = solver.generateGuess(game.constraints)
        if (game.round == 1) println(" took ${(System.currentTimeMillis() - startTime) / 1000} seconds")
        println("Guess ${game.round}: $guess  (with ${solver.candidates.solutions.size} possibilities remaining and ${solver.candidates.guesses.size} guesses available)")
        game.guess(guess)

        if (game.round == 1) {
            println("Candidate guesses generated:")
            solver.candidates.guesses.forEach { println(it) }
        }

        // evaluate the guess
        val key = Constraint.create(game.currentGuess!!, secret)
        game.evaluate(key)

        // print outcome
        if (game.won) {
            println("Fisher found the code!")
        } else {
            println("  Exact ${key.exact}  Value ${key.included}")
        }
    }

    if (!game.won) {
        println("\nSecret: $secret")
    }
}

fun generateSecret(): String {
    val secret = mutableListOf<Char>()
    for (i in 1..LENGTH) {
        secret.add(ALPHABET.random())
    }
    return secret.joinToString("")
}

fun Iterable<String>.extendCodes(iters: Int = 1): List<String> {
    val result =
        this.map { it -> List(ALPHABET_LETTERS.size) { index -> "$it${ALPHABET_LETTERS[index]}" } }
            .flatten()

    return if (iters > 1) result.extendCodes(iters - 1) else result
}