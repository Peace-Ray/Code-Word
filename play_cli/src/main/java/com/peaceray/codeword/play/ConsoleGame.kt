package com.peaceray.codeword.play

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.data.Settings
import com.peaceray.codeword.game.bot.*
import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.bot.modules.generation.CascadingGenerator
import com.peaceray.codeword.game.bot.modules.generation.CodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.generation.VocabularyFileGenerator
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumInvertedScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumScorer
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.MaximumScoreSelector
import com.peaceray.codeword.game.bot.modules.selection.MinimumScoreSelector
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.bot.modules.selection.StochasticThresholdScoreSelector
import com.peaceray.codeword.game.validators.AlphabetValidator
import com.peaceray.codeword.game.validators.VocabularyValidator
import com.peaceray.codeword.play.wordhider.LENGTH
import java.io.File
import java.util.*
import kotlin.IllegalArgumentException

class Environment(
    val game: Game,
    val solver: Solver,
    val evaluator: Evaluator,
    val evaluationPolicy: ConstraintPolicy
) {
    fun reset() {
        game.reset()
        evaluator.reset()
    }

    class Builder() {
        var length: Int? = null
        var rounds: Int? = null
        var policy: ConstraintPolicy = ConstraintPolicy.IGNORE
        var validator: ((String) -> Boolean)? = null

        var solver: Solver? = null
        var evaluator: Evaluator? = null

        var evaluationPolicy: ConstraintPolicy? = null

        fun withDimensions(length: Int, rounds: Int): Builder {
            this.length = length
            this.rounds = rounds
            return this
        }

        fun withConstraintPolicy(policy: ConstraintPolicy): Builder {
            this.policy = policy
            return this
        }

        fun withValidator(validator: (String) -> Boolean): Builder {
            this.validator = validator
            return this
        }

        fun withSolver(solver: Solver): Builder {
            this.solver = solver
            return this
        }

        fun withEvaluator(evaluator: Evaluator): Builder {
            this.evaluator = evaluator
            return this
        }

        fun withEvaluationPolicy(evaluationPolicy: ConstraintPolicy): Builder {
            this.evaluationPolicy = evaluationPolicy
            return this
        }

        fun build(): Environment {
            return Environment(
                Game(Settings(length!!, rounds!!, policy), validator!!),
                solver!!,
                evaluator!!,
                evaluationPolicy!!
            )
        }
    }
}

class ConsoleSolver(private val transform: ((String) -> String)?): Solver {
    override fun generateGuess(constraints: List<Constraint>): String {
        print("> ")
        val guess = readLine()!!
        return transform?.invoke(guess) ?: guess
    }
}

class ConsoleEvaluator(val validator: (String) -> Boolean): Evaluator {
    var code = ""

    override fun evaluate(candidate: String, constraints: List<Constraint>): Constraint {
        return Constraint.Companion.create(candidate, code)
    }

    override fun peek(constraints: List<Constraint>) = code

    override fun reset() {
        code = ""
        do {
            print("Secret: ")
            code = readLine()!!
            if (!validator(code)) {
                code = ""
                println("Invalid, try again")
            }
        } while (code.isEmpty())
    }
}

// TODO implement a console evaluator that lets users type in the markup instead of the secret

fun main() {
    while(true) {
        val env = getEnvironment()
        playGame(env)
    }
}

private fun playGame(env: Environment) {
    println()
    println()
    println("Getting started...")

    env.reset()

    println()

    // loop
    while (!env.game.over) {
        val round = env.game.round

        // attempt a guess
        do {
            try {
                val guess = env.solver.generateGuess(env.game.constraints)
                println("Guess ${round}: $guess")
                env.game.guess(guess)
            } catch (error: Game.IllegalGuessException) {
                when (error.error) {
                    Game.GuessError.LENGTH -> println("  (invalid length; use $LENGTH letters)")
                    Game.GuessError.VALIDATION -> println("  (invalid word)")
                    Game.GuessError.CONSTRAINTS -> println("  (already eliminated)")
                }
            }
        } while(env.game.state == Game.State.GUESSING)

        // evaluate the guess
        val constraint = env.evaluator.evaluate(env.game.currentGuess!!, env.game.constraints)
        if (env.evaluationPolicy == ConstraintPolicy.ALL || env.evaluationPolicy == ConstraintPolicy.POSITIVE) {
            val markup = constraint.markup.map { when(it) {
                Constraint.MarkupType.EXACT -> "O"
                Constraint.MarkupType.INCLUDED -> "i"
                Constraint.MarkupType.NO -> "_"
            } }.joinToString("")
            println("Eval  ${round}: ${markup}")
        } else if (env.evaluationPolicy == ConstraintPolicy.AGGREGATED) {
            println("Eval  ${round}: ${constraint.exact} Exact,  ${constraint.included} Included")
        }

        env.game.evaluate(constraint)
    }

    if (env.game.won) {
        println("The guesser won!")
    } else {
        println("The keeper won!\nSecret: ${env.evaluator.peek(env.game.constraints)}")
    }

    println()
    println()
    println()
}

private fun getEnvironment(): Environment {
    val builder = Environment.Builder()
    val guessTransform: ((String) -> String)?
    val generator: CandidateGenerationModule?
    var eliminationPolicy = ConstraintPolicy.ALL
    var words = true

    println("Game Type")
    println("  1) Codes, e.g. 'AAAA', 'ACAB'")
    println("  2) Words, e.g. 'tower', 'reach'")
    println("  3) Words, Hard Mode")
    print("> ")
    when(readLine()!!.toInt()) {
        1 -> {
            builder.withDimensions(4, 10)
                .withValidator(AlphabetValidator('A'..'F'))
                .withEvaluationPolicy(ConstraintPolicy.AGGREGATED)
            guessTransform = { it.toUpperCase(Locale.getDefault()) }
            generator = CodeEnumeratingGenerator('A'..'F', 4, ConstraintPolicy.IGNORE, ConstraintPolicy.AGGREGATED)
            eliminationPolicy = ConstraintPolicy.AGGREGATED
            words = false
        }
        2 -> {
            builder.withDimensions(5, 6)
                .withValidator(VocabularyValidator.fromFiles("./app/src/main/assets/words/en_5_1.txt", "./words/en_5_2.txt"))
                .withEvaluationPolicy(ConstraintPolicy.ALL)
            guessTransform = { it.toLowerCase(Locale.getDefault()) }
            generator = CascadingGenerator(
                solutions = 100,
                generators = listOf(
                    VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL),
                    VocabularyFileGenerator(listOf("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt"), ConstraintPolicy.IGNORE, ConstraintPolicy.ALL)
                )
            )
        }
        3 -> {
            builder.withDimensions(5, 6)
                .withValidator(VocabularyValidator.fromFiles("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt"))
                .withConstraintPolicy(ConstraintPolicy.ALL)
                .withEvaluationPolicy(ConstraintPolicy.ALL)
            guessTransform = { it.toLowerCase(Locale.getDefault()) }
            generator = CascadingGenerator(
                solutions = 10,
                generators = listOf(
                    VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.ALL, ConstraintPolicy.ALL),
                    VocabularyFileGenerator(listOf("./app/src/main/assets/words/en_5_1.txt", "./app/src/main/assets/words/en_5_2.txt"), ConstraintPolicy.ALL, ConstraintPolicy.ALL)
                )
            )
        }
        else -> throw IllegalArgumentException("Unrecognized")
    }

    println("Guesser Played By")
    println("  1) Me")
    println("  2) Knuth MiniMax")
    println("  3) Decision Tree")
    println("  4) Human-like Decision Tree")
    println("  5) Random Draw")
    print("> ")
    builder.solver = when(readLine()!!.toInt()) {
        1 -> ConsoleSolver(guessTransform)
        2 -> ModularSolver(
            generator,
            KnuthMinimumScorer(eliminationPolicy),
            MaximumScoreSelector()
        )
        3 -> ModularSolver(
            generator,
            InformationGainScorer(eliminationPolicy),
            MaximumScoreSelector()
        )
        4 -> {
            val commonWords = File("./app/src/main/assets/words/en_5_1.txt")
                .readLines()
                .filter { it.isNotBlank() }
                .distinct()
            val weightMap = commonWords.associateWith { if (it in commonWords) 10.0 else 1.0 }
            ModularSolver(
                generator,
                InformationGainScorer(eliminationPolicy) { weightMap[it] ?: 1.0 },
                StochasticThresholdScoreSelector(threshold = 0.8, solutionBias = 0.5)
            )
        }


        5 -> ModularSolver(generator, UnitScorer(), RandomSelector())
        else -> throw IllegalArgumentException("Unrecognized")
    }

    val secretGenerator = if (!words) generator else {
        VocabularyFileGenerator("./app/src/main/assets/words/en_5_1.txt", ConstraintPolicy.IGNORE, ConstraintPolicy.ALL)
    }

    println("Secret Keeper Played By")
    println("  1) Me")
    println("  2) Honest Bot")
    println("  3) Cheating Bot")
    print("> ")
    builder.evaluator = when(readLine()!!.toInt()) {
        1 -> ConsoleEvaluator(builder.validator!!)
        2 -> ModularHonestEvaluator(
            secretGenerator,
            UnitScorer(),
            RandomSelector()
        )
        3 -> {
            builder.rounds = 100000
            ModularFlexibleEvaluator(
                secretGenerator,
                KnuthMinimumInvertedScorer(ConstraintPolicy.ALL),
                MinimumScoreSelector(solutions = true)
            )
        }

        else -> throw IllegalArgumentException("Unrecognized")
    }

    return builder.build()
}