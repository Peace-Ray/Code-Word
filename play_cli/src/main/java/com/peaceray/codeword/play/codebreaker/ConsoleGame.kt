package com.peaceray.codeword.play.codebreaker;

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.validators.AlphabetValidator
import com.peaceray.codeword.game.data.Settings as CodeGameSettings
import java.util.*

val ALPHABET = 'A'..'F'
const val LENGTH = 4
const val ROUNDS = 10

fun main() {
    // random guess
    val secret = generateSecret()

    // create game
    val validator = AlphabetValidator(ALPHABET)
    val game = Game(CodeGameSettings(LENGTH, ROUNDS), validator)

    // loop
    while (!game.over) {
        // accept a guess
        do {
            try {
                print("Guess ${game.round}: ")
                val guess = readLine()!!
                game.guess(guess.toUpperCase(Locale.getDefault()))
            } catch (error: Game.IllegalGuessException) {
                when (error.error) {
                    Game.GuessError.LENGTH -> println("  (invalid length; use $LENGTH letters)")
                    Game.GuessError.VALIDATION -> println("  (invalid letters; use $ALPHABET)")
                    else -> println("  (unknown error)")
                }
            }
        } while(game.state == Game.State.GUESSING)

        // evaluate the guess
        val key = Constraint.create(game.currentGuess!!, secret)
        game.evaluate(key)

        // print outcome
        if (game.won) {
            println("You Win!")
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