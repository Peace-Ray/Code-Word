package com.peaceray.codeword.play

import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.*
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.play.setup.ConsoleGameEnvironmentProvider

class ConsoleSolver(private val transform: ((String) -> String)?): Solver {
    override fun generateGuess(constraints: List<Constraint>): String {
        print("> ")
        val guess = readln()
        return transform?.invoke(guess) ?: guess
    }
}

class ConsoleAutomaticEvaluator(private val transform: (String) -> String, private val validator: (String) -> Boolean): Evaluator {
    private var code = ""

    override fun evaluate(candidate: String, constraints: List<Constraint>): Constraint {
        return Constraint.Companion.create(candidate, code)
    }

    override fun peek(constraints: List<Constraint>) = code

    override fun reset() {
        code = ""
        do {
            print("Secret > ")
            code = transform(readln())
            if (!validator(code)) {
                code = ""
                println("Invalid, try again")
            }
        } while (code.isEmpty())
    }
}

class ConsoleManualEvaluator(private val transform: (String) -> String, private val validator: (String) -> Boolean): Evaluator {
    override fun evaluate(candidate: String, constraints: List<Constraint>): Constraint {
        print("  $candidate ?")
        var constraint: Constraint? = null
        do {
            print("> ")
            val evaluation = readln().lowercase().trim()
            val markupNulls = evaluation.asSequence()
                .map { when(it) {
                    'e', 'o', '!' -> Constraint.MarkupType.EXACT
                    'i', '?' -> Constraint.MarkupType.INCLUDED
                    'x', '_' -> Constraint.MarkupType.NO
                    else -> null
                } }
                .toList()
            val markup = markupNulls.filterNotNull()

            if (markupNulls.size == markup.size && markup.size == candidate.length) {
                constraint = Constraint.create(candidate, markup)
            } else {
                println("Invalid, try again")
            }
        } while (constraint == null)
        return constraint
    }

    override fun peek(constraints: List<Constraint>): String {
        var code = ""
        do {
            print("Secret: ")
            code = transform(readln())
            if (!validator(code)) {
                code = ""
                println("Invalid, try again")
            }
        } while (code.isEmpty())
        return code
    }

    override fun reset() {
        println()
        println("Think of a secret...")
        Thread.sleep(2000)
        println("  ...got one?")
        Thread.sleep(500)
        println("  Evaluate guesses with per-letter markup, e.g.")
        println("  Guess 1: tower")
        println("  > _OOi_")
        println("  Exact: OoEe!, Included: Ii?, No: Xx_")
        Thread.sleep(500)
        println()
    }
}

fun applyMarkup(text: String, markup: Constraint.MarkupType?): String {
    val esc = "\u001b["
    val endCode = "m"
    val reset = "0"
    val red = "31"
    val green = "32"
    val yellow = "33"
    val blue = "34"
    val gray = "90"
    val white = "97"
    val grayBG = "100"
    val bold = "1"
    val faint = "2"

    val codes = when (markup) {
        Constraint.MarkupType.EXACT -> setOf(bold, blue)
        Constraint.MarkupType.INCLUDED -> setOf(bold, yellow)
        Constraint.MarkupType.NO -> setOf(white)
        null -> setOf(gray)
    }

    return if (codes.isEmpty()) text else {
        esc + codes.joinToString(";") + endCode + text + esc + reset + endCode
    }
}

fun applyMarkup(text: String, markup: List<Constraint.MarkupType?>): String {
    return text.mapIndexed { index, c -> applyMarkup("$c", markup[index]) }
        .joinToString("")
}
fun applyMarkup(text: String, charMarkup: Map<Char, Constraint.MarkupType?>): String {
    return applyMarkup(text, text.map { char -> charMarkup[char] })
}

fun printEvaluation(constraint: Constraint?, env: ConsoleGameEnvironment) {
    val round: Int
    val markupGuess: String
    if (constraint != null) {
        round = env.game.constraints.lastIndexOf(constraint) + 1
        val mkExact = applyMarkup("${constraint.exact}", Constraint.MarkupType.EXACT)
        val mkInclude = applyMarkup("${constraint.included}", Constraint.MarkupType.INCLUDED)
        val mkBoth = applyMarkup("${constraint.exact + constraint.included}", Constraint.MarkupType.INCLUDED)
        markupGuess = when (env.feedbackPolicy) {
            ConstraintPolicy.PERFECT,
            ConstraintPolicy.ALL,
            ConstraintPolicy.POSITIVE -> applyMarkup(constraint.candidate.uppercase(), constraint.markup)
            ConstraintPolicy.AGGREGATED -> "${constraint.candidate} $mkExact $mkInclude"
            ConstraintPolicy.AGGREGATED_INCLUDED -> "${constraint.candidate} $mkBoth"
            ConstraintPolicy.AGGREGATED_EXACT -> "${constraint.candidate} $mkExact"
            ConstraintPolicy.IGNORE -> constraint.candidate.uppercase()
        }
    } else {
        round = 0
        markupGuess = List(env.game.settings.letters) { "_" }.joinToString("")
    }
    val feedback = getCharacterFeedback(env)

    val label = if (round > 0) "Guess ${round}:  " else "Secret:   "
    print("$label$markupGuess")
    if (feedback == null) println() else println("    $feedback")
}

fun getCharacterFeedback(env: ConsoleGameEnvironment): String? {
    var feedbackText: String? = null
    env.feedbackProvider
        ?.getCharacterFeedback(env.feedbackPolicy, env.game.constraints)
        ?.let { feedback ->
            feedbackText = applyMarkup(
                feedback.keys.sorted().joinToString("").uppercase(),
                feedback.values.associate { cf -> Pair(cf.character.uppercaseChar(), cf.markup) }
            )
        }
    return feedbackText
}

fun main() {
    val environmentProvider = ConsoleGameEnvironmentProvider()
    while(true) {
        val env = environmentProvider.getEnvironment()
        playGame(env)
    }
}

private fun playGame(env: ConsoleGameEnvironment) {
    println()
    println("Getting started...")
    println()

    env.reset()

    // print introduction and feedback
    printEvaluation(null, env)

    // loop
    while (!env.game.over) {
        // attempt a guess
        do {
            try {
                val guess = env.solver.generateGuess(env.game.constraints)
                env.game.guess(guess)
            } catch (error: Game.IllegalGuessException) {
                when (error.error) {
                    Game.GuessError.LENGTH -> println("  (invalid length; use ${env.game.settings.letters} letters)")
                    Game.GuessError.VALIDATION -> println("  (invalid word)")
                    Game.GuessError.CONSTRAINTS -> println("  (already eliminated)")
                }
            }
        } while(env.game.state == Game.State.GUESSING)

        // evaluate the guess
        val constraint = env.evaluator.evaluate(env.game.currentGuess!!, env.game.constraints)
        env.game.evaluate(constraint)

        // print guess with markup and markup
        printEvaluation(constraint, env)
    }

    if (env.game.won) {
        println("The guesser won!")
    } else {
        println("The keeper won!\nSecret: ${env.evaluator.peek(env.game.constraints)}")
    }

    println()
    println()
}