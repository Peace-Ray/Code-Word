package com.peaceray.codeword.play.setup

import com.google.gson.GsonBuilder
import com.peaceray.codeword.game.bot.ModularFlexibleEvaluator
import com.peaceray.codeword.game.bot.ModularHonestEvaluator
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.game.bot.modules.generation.CandidateGenerationModule
import com.peaceray.codeword.game.bot.modules.generation.CascadingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.CodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.OneCodeEnumeratingGenerator
import com.peaceray.codeword.game.bot.modules.generation.enumeration.SolutionTruncatedEnumerationCodeGenerator
import com.peaceray.codeword.game.bot.modules.generation.vocabulary.VocabularyFileGenerator
import com.peaceray.codeword.game.bot.modules.scoring.InformationGainScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumInvertedScorer
import com.peaceray.codeword.game.bot.modules.scoring.KnuthMinimumScorer
import com.peaceray.codeword.game.bot.modules.scoring.UnitScorer
import com.peaceray.codeword.game.bot.modules.selection.MaximumScoreSelector
import com.peaceray.codeword.game.bot.modules.selection.MinimumScoreSelector
import com.peaceray.codeword.game.bot.modules.selection.RandomSelector
import com.peaceray.codeword.game.bot.modules.selection.StochasticThresholdScoreSelector
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.providers.InferredMarkupFeedbackProvider
import com.peaceray.codeword.game.validators.Validator
import com.peaceray.codeword.game.validators.Validators
import com.peaceray.codeword.play.ConsoleAutomaticEvaluator
import com.peaceray.codeword.play.ConsoleGameEnvironment
import com.peaceray.codeword.play.ConsoleManualEvaluator
import com.peaceray.codeword.play.ConsoleSolver
import java.io.File
import java.nio.file.Paths
import kotlin.math.max

class ConsoleGameEnvironmentProvider {

    //region Provide Environment
    //---------------------------------------------------------------------------------------------

    private var loopCount = 0

    fun getEnvironment(): ConsoleGameEnvironment {
        val settings = getSettings()
        return getEnvironment(settings)
    }

    private fun getEnvironment(settings: ConsoleGameSettings): ConsoleGameEnvironment {
        val uppercase = !settings.vocabulary.words
        val characters = (if (settings.vocabulary.words) 'a'..'z' else 'A'..'Z')
            .toList().subList(0, settings.vocabulary.characterCount).toSet()

        val builder = ConsoleGameEnvironment.Builder()

        val validator: Validator
        val generator: CandidateGenerationModule
        val secretGenerator: CandidateGenerationModule
        val cheaterSecretGenerator: CandidateGenerationModule
        val generatorSP = settings.difficulty.feedbackPolicy
        val generatorGP = if (settings.difficulty.hard && generatorSP == ConstraintPolicy.ALL) {
            ConstraintPolicy.POSITIVE
        } else {
            ConstraintPolicy.IGNORE
        }
        val guessTransform: ((String) -> String) = if (uppercase) { it -> it.uppercase() } else { it -> it.lowercase() }

        // game dimensions
        builder.length = settings.vocabulary.length
        builder.rounds = when {
            settings.difficulty.feedbackPolicy.isByLetter() -> 6
            settings.vocabulary.length >= 8 -> 12
            else -> 10
        }

        // vocabulary: validation and generation
        builder.policy = generatorGP
        if (settings.vocabulary.words) {
            validator = Validators.all(
                Validators.vocabulary(File("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/valid.txt")),
                Validators.characterOccurrences(max = if (settings.vocabulary.repetitions) settings.vocabulary.length else 1)
            )
            builder.validator = validator
            generator = CascadingGenerator(
                solutions = 100,
                generators = listOf(
                    VocabularyFileGenerator("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/secrets-90.txt", generatorGP, generatorSP, filter = validator),
                    VocabularyFileGenerator("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/secrets.txt", generatorGP, generatorSP, filter = validator),
                    VocabularyFileGenerator("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/guesses.txt", generatorGP, generatorSP, filter = validator,
                        solutionFilename = "./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/secrets.txt"),
                    VocabularyFileGenerator("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/guesses.txt", generatorGP, generatorSP, filter = validator)
                )
            )
            secretGenerator = VocabularyFileGenerator("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/secrets-995.txt", generatorGP, generatorSP, filter = validator)
            cheaterSecretGenerator = secretGenerator
        } else {
            validator = Validators.all(
                Validators.alphabet(characters),
                Validators.characterOccurrences(max = if (settings.vocabulary.repetitions) settings.vocabulary.length else 1)
            )
            builder.validator = validator
            generator = CodeEnumeratingGenerator(
                characters,
                settings.vocabulary.length,
                generatorGP,
                generatorSP,
                maxOccurrences = if (settings.vocabulary.repetitions) settings.vocabulary.length else 1,
                shuffle = true,
                truncateAtProduct = 5000000,
                truncateAtLength = 50000
            )
            secretGenerator = OneCodeEnumeratingGenerator(
                characters,
                settings.vocabulary.length,
                maxOccurrences = if (settings.vocabulary.repetitions) settings.vocabulary.length else 1
            )
            cheaterSecretGenerator = SolutionTruncatedEnumerationCodeGenerator(
                characters,
                settings.vocabulary.length,
                generatorSP,
                maxOccurrences = if (settings.vocabulary.repetitions) settings.vocabulary.length else 1,
                shuffle = true,
                truncateAtSize = 5000000,
                pretruncateAtSize = 5000000
            )
        }

        // solver
        builder.solver = when (settings.players.guesser) {
            ConsoleGameSettings.Players.Guesser.PLAYER -> {
                ConsoleSolver(guessTransform)
            }
            ConsoleGameSettings.Players.Guesser.KNUTH_MINIMAX -> ModularSolver(
                generator,
                KnuthMinimumScorer(generatorSP),
                MaximumScoreSelector()
            )
            ConsoleGameSettings.Players.Guesser.DECISION_TREE -> ModularSolver(
                generator,
                InformationGainScorer(generatorSP),
                MaximumScoreSelector()
            )
            ConsoleGameSettings.Players.Guesser.REALISTIC_DECISION_TREE -> {
                val weightMap: Map<String, Double> = if (!settings.vocabulary.words) emptyMap() else {
                    val commonWords = File("./app/src/main/assets/words/en-US/standard/length-${settings.vocabulary.length}/secrets-90.txt")
                        .readLines()
                        .filter { it.isNotBlank() }
                        .distinct()
                    commonWords.associateWith { if (it in commonWords) 10.0 else 1.0 }
                }

                ModularSolver(
                    generator,
                    InformationGainScorer(generatorSP) { weightMap[it] ?: 1.0 },
                    StochasticThresholdScoreSelector(threshold = 0.8, solutionBias = 0.5)
                )
            }
            ConsoleGameSettings.Players.Guesser.RANDOM_DRAW -> ModularSolver(
                generator,
                UnitScorer(),
                RandomSelector()
            )
        }

        // evaluator
        builder.evaluator = when (settings.players.keeper) {
            ConsoleGameSettings.Players.Keeper.PLAYER_AUTOMATIC -> ConsoleAutomaticEvaluator(guessTransform, validator)
            ConsoleGameSettings.Players.Keeper.PLAYER_MANUAL -> ConsoleManualEvaluator(guessTransform, validator)
            ConsoleGameSettings.Players.Keeper.HONEST_BOT -> ModularHonestEvaluator(
                secretGenerator,
                UnitScorer(),
                RandomSelector()
            )
            ConsoleGameSettings.Players.Keeper.CHEATING_BOT -> {
                builder.rounds = 10000
                ModularFlexibleEvaluator(
                    cheaterSecretGenerator,
                    KnuthMinimumInvertedScorer(ConstraintPolicy.ALL),
                    MinimumScoreSelector(solutions = true)
                )
            }
        }

        // feedback
        builder.feedbackProvider = when (settings.difficulty.letterFeedback) {
            ConsoleGameSettings.Difficulty.LetterFeedback.NONE -> InferredMarkupFeedbackProvider(
                characters,
                settings.vocabulary.length,
                if (settings.vocabulary.repetitions) settings.vocabulary.length else 1,
                setOf(InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION)
            )
            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT -> InferredMarkupFeedbackProvider(
                characters,
                settings.vocabulary.length,
                if (settings.vocabulary.repetitions) settings.vocabulary.length else 1,
                setOf(InferredMarkupFeedbackProvider.MarkupPolicy.DIRECT, InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION)
            )
            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT_AND_INFERRED -> InferredMarkupFeedbackProvider(
                characters,
                settings.vocabulary.length,
                if (settings.vocabulary.repetitions) settings.vocabulary.length else 1,
                setOf(InferredMarkupFeedbackProvider.MarkupPolicy.SOLUTION, InferredMarkupFeedbackProvider.MarkupPolicy.INFERRED)
            )
        }
        builder.feedbackPolicy = settings.difficulty.feedbackPolicy

        return builder.build()
    }

    private fun getSettings(): ConsoleGameSettings {
        val previousSettingsName = "previous"
        var settings = loadSettings(previousSettingsName)?.settings ?: ConsoleGameSettings()

        if (--loopCount > 0) return settings

        // prepare basic prompts
        val mainOptionPrompts = mutableListOf (
            settings.vocabulary.toPromptOption(0),
            settings.difficulty.toPromptOption(1),
            settings.players.toPromptOption(2),
            PromptOption(3,"Play", setOf("play", "go", "now", "start", "begin")),
            PromptOption(4, "Print", hidden = true),
            PromptOption(5, "List", setOf("games", "saves"), hidden = true),
            PromptOption(6, "Loop", setOf("iterate"), hidden = true)
        )
        val savedSettingsOffset = mainOptionPrompts.size

        // load settings as hidden prompts
        val savedSettingsWrappers = getSavedSettings()
            .filter { it != previousSettingsName }
            .mapNotNull { loadSettings(it) }

        val savedSettingsOptionPrompts = savedSettingsWrappers.mapIndexed { index, settingsWrapper ->
            PromptOption(
                value = index + savedSettingsOffset,
                title = settingsWrapper.title,
                aliases = settingsWrapper.aliases,
                hidden = true
            )
        }

        while (true) {
            // refresh settings toPromptOptions
            mainOptionPrompts[0] = settings.vocabulary.toPromptOption(0)
            mainOptionPrompts[1] = settings.difficulty.toPromptOption(1)
            mainOptionPrompts[2] = settings.players.toPromptOption(2)

            val options = listOf(mainOptionPrompts, savedSettingsOptionPrompts).flatten()

            when (val selection = promptSelection("Game Type", options)) {
                0 -> {
                    // set vocabulary
                    val gameType = promptSelection(
                        "Vocabulary Type",
                        PromptOption(0, "Codes, e.g. 'AAAA', 'ACAF'"),
                        PromptOption(1, "Words, e.g. 'tower', 'reach', 'roots'")
                    )
                    val words = gameType == 1
                    val length = promptInt(
                        if (words) "Word length (3-12)" else "Code length (3-8)",
                        if (words) 3..12 else 3..8
                    )
                    val characterCount = if (words) 26 else promptInt("Code chars (3-12)", 3..12)
                    val repetitions = promptBoolean("Allow letter repetitions")
                    settings = settings.with(ConsoleGameSettings.Vocabulary(words, length, characterCount, repetitions))
                }
                1 -> {
                    // set difficulty
                    val feedbackPolicy = promptSelection(
                        "Feedback type",
                        PromptOption(
                            ConstraintPolicy.POSITIVE,
                            "Direct letter annotation",
                            setOf("word", "letter", "character")
                        ),
                        PromptOption(
                            ConstraintPolicy.AGGREGATED,
                            "Aggregated (exact/included) counts",
                            setOf("master", "counts")
                        ),
                        PromptOption(
                            ConstraintPolicy.AGGREGATED_INCLUDED,
                            "Included count",
                            setOf("j")
                        ),
                        PromptOption(
                            ConstraintPolicy.AGGREGATED_EXACT,
                            "Exact counts",
                            setOf("match")
                        )
                    )

                    val hardMode = if (!feedbackPolicy.isByLetter()) false else promptBoolean(
                        "Hard mode"
                    )

                    val letterFeedback = promptSelection(
                        "Keyboard Hints",
                        PromptOption(
                            ConsoleGameSettings.Difficulty.LetterFeedback.NONE,
                            "None",
                            setOf("no", "empty", "silent")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT,
                            "Direct Only",
                            setOf("word", "elimination")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT_AND_INFERRED,
                            "Yes",
                            setOf("full", "indirect", "complete")
                        ),
                    )

                    settings = settings.with(ConsoleGameSettings.Difficulty(hardMode, feedbackPolicy, letterFeedback))
                }
                2 -> {
                    // set players
                    val guesser = promptSelection(
                        "Guesser Played By",
                        PromptOption(
                            ConsoleGameSettings.Players.Guesser.PLAYER,
                            "Me",
                            setOf("player", "human", "person", "keyboard")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Guesser.KNUTH_MINIMAX,
                            "Knuth Minimax",
                            setOf("knuth", "minimax", "minmax", "max")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Guesser.DECISION_TREE,
                            "Decision Tree",
                            setOf("tree")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Guesser.REALISTIC_DECISION_TREE,
                            "Realistic Decision Tree",
                            setOf("realistic")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Guesser.RANDOM_DRAW,
                            "Random Draw",
                            setOf("random", "draw")
                        ),
                    )

                    val keeper = promptSelection(
                        "Secret Keeper Played By",
                        PromptOption(
                            ConsoleGameSettings.Players.Keeper.PLAYER_AUTOMATIC,
                            "Me (automatically)",
                            setOf("automatically", "easy", "simple")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Keeper.PLAYER_MANUAL,
                            "Me (manually)",
                            setOf("manually", "hard")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Keeper.HONEST_BOT,
                            "Honest Bot",
                            setOf("bot", "cpu", "computer")
                        ),
                        PromptOption(
                            ConsoleGameSettings.Players.Keeper.CHEATING_BOT,
                            "Cheating Bot",
                            setOf("absurd", "flexible")
                        )
                    )

                    settings = settings.with(ConsoleGameSettings.Players(guesser, keeper))
                }
                3 -> {
                    // play
                    saveSettings(previousSettingsName, SettingsWrapper(previousSettingsName, setOf(), settings))
                    return settings
                }
                4 -> {
                    // print current settings
                    println()
                    println(gson.toJson(SettingsWrapper(settings = settings)))
                    println()
                    println("Paste the above text into a settings file, e.g. ${"exampleName".toFile().absolutePath},")
                    println("and replace the 'name' and 'aliases' properties.")
                    println()
                }
                5 -> {
                    // list available games
                    println("")
                    println("Available game modes:")
                    savedSettingsWrappers.forEach { println("  ${it.title}") }
                }
                6 -> {
                    // loop
                    loopCount = promptInt("Iterations", 2..1000)
                    saveSettings(previousSettingsName, SettingsWrapper(previousSettingsName, setOf(), settings))
                    return settings
                }
                else -> {
                    val savedSettings = savedSettingsWrappers[selection - savedSettingsOffset]
                    println("${savedSettings.title}!")
                    settings = savedSettings.settings
                }
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Load Settings
    //---------------------------------------------------------------------------------------------

    private data class SettingsWrapper(
        val title: String = "ReplaceNameHere",
        val aliases: Set<String> = setOf("replace", "aliases", "here"),
        val settings: ConsoleGameSettings
    )

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val dataDir = Paths.get("", "play_cli", "save_data").toAbsolutePath()

    init {
        dataDir.toFile().mkdirs()
    }

    private fun String.toFile(): File {
        return File(dataDir.toString(), "$this.json")
    }

    private fun loadSettings(name: String): SettingsWrapper? {
        return try {
            val save = name.toFile().readText()
            gson.fromJson(save, SettingsWrapper::class.java)
        } catch (error: Exception) {
            null
        }
    }

    private fun saveSettings(name: String, settings: SettingsWrapper) {
        val json = gson.toJson(settings)
        name.toFile().writeText(json)
    }

    private fun getSavedSettings(): List<String> {
        return dataDir.toFile().walkTopDown().map {
            val path = it.absolutePath
            val parentPath = dataDir.toString()
            if (path.endsWith(".json")) path.substring(parentPath.length + 1, path.length - 5) else null
        }.filterNotNull().toList()
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Prompts
    //---------------------------------------------------------------------------------------------

    data class PromptOption<T>(
        val value: T,
        val title: String,
        val aliases: Collection<String> = emptySet(),
        val hidden: Boolean = false
    )

    private fun promptInt(prompt: String, range: IntRange): Int {
        // output prompt
        print("$prompt ")

        // get user input
        var selection: Int? = null
        while (selection == null) {
            print("> ")

            val input = readln()
            try {
                val inputInt = input.toInt()
                if (inputInt in range) {
                    selection = inputInt
                } else {
                    println("  (out of range)")
                }
            } catch (error: Exception) {
                println("  (not a number)")
            }
        }

        return selection
    }

    private fun promptBoolean(prompt: String): Boolean = promptSelection(
        prompt,
        PromptOption(true, "yes", setOf("true", "on", "1"), hidden = true),
        PromptOption(false, "no", setOf("false", "off", "0"), hidden = true)
    )

    private fun <T> promptSelection(
        prompt: String,
        vararg options: PromptOption<T>
    ) = promptSelection(prompt, options.toList())

    private fun <T> promptSelection(
        prompt: String,
        options: List<PromptOption<T>>
    ): T {
        val (visibleOptions, invisibleOptions) = options.partition { !it.hidden }
        val orderedOptions = listOf(visibleOptions, invisibleOptions).flatten()

        val lowercaseOptions = orderedOptions.map {
            PromptOption(
                it.value,
                it.title.lowercase(),
                it.aliases.map { it.lowercase() },
                it.hidden
            )
        }
        val lowercaseAliases = lowercaseOptions.map {
            listOf(it.aliases, listOf(it.title)).flatten()
        }

        // verify no overlapping aliases
        val flatAliases = lowercaseOptions.flatMap { it.aliases }.map { it.lowercase() }
        if (flatAliases.size != flatAliases.distinct().size) {
            throw IllegalArgumentException("Option aliases are not distinct")
        }

        // build alias list by truncating inputs
        val optionAliases = List(lowercaseOptions.size) { mutableSetOf<String>() }
        lowercaseAliases.forEachIndexed { optionIndex, aliases ->
            aliases.forEachIndexed { aliasIndex, alias ->
                // find shortest unique alias, comparing only OTHER options (okay if overlap w/in this one)
                var len = lowercaseAliases.filterIndexed { index, _ -> index != optionIndex }
                    .flatten()
                    .fold(1) { len, otherAlias ->
                        max(len, alias.commonPrefixWith(otherAlias).length + 1)
                    }
                // insert as a substring
                optionAliases[optionIndex].add(if (len > alias.length) alias else alias.substring(0, len))
            }
        }

        // output prompt
        if (visibleOptions.isNotEmpty()) println(prompt) else print("$prompt ")
        visibleOptions.forEachIndexed { index, option ->
            println("  ${index + 1}) ${option.title}")
        }

        // get user input
        var selection: PromptOption<T>? = null
        while (selection == null) {
            print("> ")

            val input = readln()
            try {
                val inputInt = input.toInt()
                selection = visibleOptions[inputInt - 1]
            } catch (error: Exception) {
                val optionIndex = optionAliases.indexOfFirst { aliases -> aliases.any { input.startsWith(it) } }
                if (optionIndex >= 0) selection = orderedOptions[optionIndex]
            }

            if (selection == null) {
                println("  (unrecognized)")
            }
        }

        return selection.value
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region ConsoleGameSettings to Prompts
    //---------------------------------------------------------------------------------------------

    fun <T> ConsoleGameSettings.Vocabulary.toPromptOption(value: T): PromptOption<T> {
        val title = when {
            words && repetitions -> "Secret Word (length $length), e.g.'tower', 'roots'"
            words -> "Secret Word (length $length w/o letter repetitions), e.g. 'tower', 'reach'"
            repetitions -> "Secret Code (length $length, chars $characterCount), e.g. 'AAAA', 'ACAF'"
            else -> "Secret Code (length $length, chars $characterCount w/o letter repetitions), e.g. 'ABCD', 'ADBF'"
        }

        val aliases = setOf(
            "secret", "words", "codes", "length", "characters", "letters", "alphabet", "vocabulary",
            "repetitions", "occurrences", "game", "type"
        )

        return PromptOption(value, title, aliases)
    }

    fun <T> ConsoleGameSettings.Difficulty.toPromptOption(value: T): PromptOption<T> {
        val elements = mutableListOf<String>()
        when (feedbackPolicy) {
            ConstraintPolicy.IGNORE -> elements.add("No feedback")
            ConstraintPolicy.AGGREGATED_EXACT -> elements.add("Exact letter count")
            ConstraintPolicy.AGGREGATED_INCLUDED -> elements.add("Included letter count")
            ConstraintPolicy.AGGREGATED -> elements.add("Exact/Included counts")
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> elements.add("Per-letter feedback")
        }

        if (hard) elements.add("Hard Mode")

        when (letterFeedback) {
            ConsoleGameSettings.Difficulty.LetterFeedback.NONE -> {
                elements.add("Hints: No")
            }
            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT -> {
                elements.add("Hints: Direct feedback only")
            }
            ConsoleGameSettings.Difficulty.LetterFeedback.DIRECT_AND_INFERRED -> {
                elements.add("Hints: Yes")
            }
        }

        val title = elements.joinToString(", ")

        val aliases = setOf(
            "difficulty", "feedback", "markup", "hint", "keyboard", "constraint", "hard", "easy"
        )

        return PromptOption(value, title, aliases)
    }

    fun <T> ConsoleGameSettings.Players.toPromptOption(value: T): PromptOption<T> {
        val elements = mutableListOf<String>()

        elements.add("Guesser: ${when (guesser) {
            ConsoleGameSettings.Players.Guesser.PLAYER -> "me"
            ConsoleGameSettings.Players.Guesser.KNUTH_MINIMAX -> "Knuth minimax"
            ConsoleGameSettings.Players.Guesser.DECISION_TREE -> "decision tree"
            ConsoleGameSettings.Players.Guesser.REALISTIC_DECISION_TREE -> "human-like decision tree"
            ConsoleGameSettings.Players.Guesser.RANDOM_DRAW -> "random draw"
        }}")

        elements.add("Secret keeper: ${when (keeper) {
            ConsoleGameSettings.Players.Keeper.PLAYER_AUTOMATIC -> "me (automatically)"
            ConsoleGameSettings.Players.Keeper.PLAYER_MANUAL -> "me (manually)"
            ConsoleGameSettings.Players.Keeper.HONEST_BOT -> "honest bot"
            ConsoleGameSettings.Players.Keeper.CHEATING_BOT -> "cheating bot"
        }}")

        val title = elements.joinToString(", ")

        val aliases = setOf(
            "players", "guesser", "keeper", "solver", "evaluator", "me", "knuth", "decision",
            "random", "automatically", "manually", "honest", "cheating", "bot"
        )

        return PromptOption(value, title, aliases)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}