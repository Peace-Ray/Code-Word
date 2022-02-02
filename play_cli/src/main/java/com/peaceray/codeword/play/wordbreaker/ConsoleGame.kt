package com.peaceray.codeword.play.wordbreaker;

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.bot.ModularHonestEvaluator
import com.peaceray.codeword.game.bot.modules.generation.VocabularyFileGenerator
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.validators.VocabularyValidator
import com.peaceray.codeword.game.data.Settings as CodeGameSettings
import java.util.*

const val LENGTH = 5
const val ROUNDS = 10000

fun main() {
    // create game
    val validator = VocabularyValidator.fromFiles("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt")
    val game = Game(CodeGameSettings(LENGTH, ROUNDS), validator)

    // create evaluator
    val evaluator = ModularHonestEvaluator(
        VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL),
        UnitScorer(),
        RandomSelector()
    )

    // loop
    while (!game.over) {
        val round = game.round

        // accept a guess
        do {
            try {
                print("Guess ${round}: ")
                val guess = (readLine()!!).toLowerCase(Locale.getDefault())
                if (guess == "hint") {
                    println("\n  Hint: try ${evaluator.peek(game.constraints)}")
                } else {
                    game.guess(guess)
                }
            } catch (error: Game.IllegalGuessException) {
                when (error.error) {
                    Game.GuessError.LENGTH -> println("  (invalid length; use $LENGTH letters)")
                    Game.GuessError.VALIDATION -> println("  (invalid word)")
                    else -> println("  (unknown error)")
                }
            }
        } while(game.state == Game.State.GUESSING)

        // evaluate the guess
        val constraint = evaluator.evaluate(game.currentGuess!!, game.constraints)
        game.evaluate(constraint)

        // print outcome
        if (game.won) {
            println("You Win!")
        } else {
            val markup = constraint.markup.map { when(it) {
                Constraint.MarkupType.EXACT -> "O"
                Constraint.MarkupType.INCLUDED -> "i"
                Constraint.MarkupType.NO -> "_"
            } }.joinToString("")
            println("Eval  ${round}: ${markup}")
        }
    }

    if (!game.won) {
        println("\nSecret: ${evaluator.peek(game.constraints)}")
    }
}
