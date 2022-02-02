package com.peaceray.codeword.play.wordfisher;

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.ModularHonestEvaluator
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.game.bot.modules.generation.CascadingGenerator
import com.peaceray.codeword.game.bot.modules.generation.VocabularyFileGenerator
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.bot.modules.selection.StochasticThresholdScoreSelector
import com.peaceray.codeword.game.validators.VocabularyValidator
import com.peaceray.codeword.game.data.Settings as CodeGameSettings

const val LENGTH = 5
const val ROUNDS = 10

fun main() {
    // get a list of common words
    val commonWords = VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL)
        .guessVocabulary
        .toSet()

    // random selection evaluator
    val evaluator = ModularHonestEvaluator(
        VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL),
        UnitScorer(),
        RandomSelector()
    )

    // create solver
    val solver = ModularSolver(
        CascadingGenerator(
            guesses = 100,
            generators = listOf(
                VocabularyFileGenerator("./app/src/main/assets/words/en_5_0.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL),
                VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL),
                VocabularyFileGenerator(listOf("./app/src/main/assets/words/en_5_1.txt"), ConstraintPolicy.IGNORE, ConstraintPolicy.ALL, guessFilenames = listOf(
                    "./app/src/main/assets/words/en_5_1.txt",
                    "./app/src/main/assets/words/en_5_2.txt"
                )),
                VocabularyFileGenerator(listOf("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt"), ConstraintPolicy.IGNORE, ConstraintPolicy.ALL)
            )
        ),
        InformationGainScorer(ConstraintPolicy.ALL), // { if (it in commonWords) 1000.0 else 1.0 },
        StochasticThresholdScoreSelector(solutionBias = 0.3)
    )

    // create game
    val validator = VocabularyValidator.fromFiles("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt")
    val game = Game(CodeGameSettings(LENGTH, ROUNDS), validator)

    // first operation takes a while...
    print("Getting started")
    val startTime = System.currentTimeMillis()

    // loop
    while (!game.over) {
        // attempt a guess
        val guess = solver.generateGuess(game.constraints)
        if (game.round == 1) println(" took ${(System.currentTimeMillis() - startTime) / 1000.0} seconds")
        println("Guess ${game.round}: $guess  (with ${solver.candidates.solutions.size} possibilities remaining)")
        game.guess(guess)

        // evaluate the guess
        val key = evaluator.evaluate(guess, game.constraints)
        game.evaluate(key)

        // print outcome
        if (game.won) {
            println("Fisher found the code!")
        } else {
            println("  Exact ${key.exact}  Value ${key.included}")
        }
    }

    if (!game.won) {
        println("\nSecret: ${evaluator.peek(game.constraints)}")
    }
}