package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.managers.game.GameSessionManager
import com.peaceray.codeword.domain.managers.game.GameSetupManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.presentation.contracts.CodeGameContract
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * A Presenter for interactive CodeGame. Retrieves the appropriate Solver and Evaluator implementations
 * from Managers (if any) and handles message passing (game moves) between the player and bots.
 *
 * The Evaluator and Solver perform complicated, long-running operations; their construction is also
 * non-trivial. To allow responsive UI, this Presenter uses RxJava when interacting with these
 * intensive operations.
 */
class CodeGamePresenter @Inject constructor(): CodeGameContract.Presenter, BasePresenter<CodeGameContract.View>() {
    @Inject lateinit var gameSetupManager: GameSetupManager
    @Inject lateinit var gameSessionManager: GameSessionManager

    val gameSetup: GameSetup by lazy { gameSetupManager.getSetup() }
    val game: Game by lazy { gameSessionManager.createGame(gameSetup) }

    private val locale: Locale = Locale.getDefault()

    private val solverObservable: Single<Solver> by lazy {
        Single.defer {
            Timber.v("Creating Solver")
            val solver = gameSessionManager.getSolver(gameSetup)
            Single.just(solver)
        }.subscribeOn(Schedulers.io())
            .cache()
    }

    private val evaluatorObservable: Single<Evaluator> by lazy {
        Single.defer {
            Timber.v("Creating Evaluator")
            val evaluator = gameSessionManager.getEvaluator(gameSetup)
            Single.just(evaluator)
        }.subscribeOn(Schedulers.io())
            .cache()
    }

    private var disposable: Disposable = Disposable.disposed()

    override fun onAttached() {
        super.onAttached()

        // set board size
        if (gameSetup.board.rounds == 0) {
            view?.setGameFieldUnlimited(gameSetup.board.letters)
        } else {
            view?.setGameFieldSize(gameSetup.board.letters, gameSetup.board.rounds)
        }

        // TODO restore any persisted game?

        // preload Solver and Evaluator (as needed)
        if (gameSetup.solver != GameSetup.Solver.PLAYER) {
            solverObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe { solver -> Timber.v("Preloaded Solver $solver") }
        }

        if (gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
            evaluatorObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe { evaluator -> Timber.v("Preloaded Evaluator $evaluator") }
        }

        // next step!
        advanceGameCharacterEvaluations()
    }

    override fun onDetached() {
        super.onDetached()

        disposable.dispose()
    }

    override fun onGuessUpdated(before: String, after: String) {
        Timber.v("onGuessUpdate for characters $before -> $after")
        // all codes lowercase
        val charset = gameSessionManager.getCodeCharacters(gameSetup)
        val ok = after.length <= gameSetup.board.letters
                && after.toLowerCase(locale).all { it in charset }
                && game.state == Game.State.GUESSING
                && gameSetup.solver == GameSetup.Solver.PLAYER
        if (ok) view?.setGuess(after.toLowerCase(locale))
    }

    override fun onGuess(guess: String) {
        when {
            gameSetup.solver != GameSetup.Solver.PLAYER -> TODO("no human guesser")
            game.state != Game.State.GUESSING -> TODO("not accepting guesses")
            else -> try {
                // convention: all codes lowercase
                val sanitizedGuess = guess.toLowerCase(locale)
                // TODO applying a guess can take time to check constraints; do off main thread?
                game.guess(sanitizedGuess)
                advanceGame()
            } catch (err: Game.IllegalGuessException) {
                Timber.e(err, "Couldn't apply guess")
                when(err.error) {
                    Game.GuessError.LENGTH -> TODO()
                    Game.GuessError.VALIDATION -> TODO()
                    Game.GuessError.CONSTRAINTS -> TODO()
                }
            }
        }
    }

    override fun onEvaluation(guess: String, markup: List<Constraint.MarkupType>) {
        when {
            gameSetup.evaluator != GameSetup.Evaluator.PLAYER -> TODO("no human evaluator")
            game.state != Game.State.EVALUATING -> TODO("not accepting evaluations right now")
            else -> try {
                // convention: all codes lowercase
                val constraint = Constraint.create(guess.toLowerCase(locale), markup)
                game.evaluate(constraint)
                view?.replaceGuessWithConstraint(constraint)
                advanceGame()
            } catch (err: Game.IllegalEvaluationException) {
                when(err.error) {
                    Game.EvaluationError.GUESS -> TODO()
                }
            }
        }
    }

    override fun onEvaluation(guess: String, exact: Int, included: Int) {
        TODO("Not yet implemented")
    }

    //region Game Progression
    //---------------------------------------------------------------------------------------------
    private fun advanceGame() {
        Timber.v("advanceGame")
        when (game.state) {
            Game.State.GUESSING -> advanceGameGuessing()
            Game.State.EVALUATING -> advanceGameEvaluating()
            Game.State.WON, Game.State.LOST -> advanceGameOver()
        }
    }

    private fun advanceGameCharacterEvaluations() {
        disposable.dispose()
        disposable = computeCharacterEvaluations(game.constraints)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { solution ->
                    view?.setCharacterEvaluations(solution)
                    advanceGame()
                },
                { cause ->
                    Timber.e(cause, "Error computing character constraints")
                    // TODO display error to user?
                }
            )
    }

    private fun advanceGameGuessing() {
        if (gameSetup.solver == GameSetup.Solver.PLAYER) {
            view?.promptForGuess()
        } else {
            Timber.v("About to compute a solution")
            val time = System.currentTimeMillis()
            view?.promptForWait()
            disposable.dispose()
            disposable = computeSolution(game.constraints)
                .delay(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { solution ->
                        Timber.v("Computed a solution: $solution. Took ${(System.currentTimeMillis() - time) / 1000.0} seconds")
                        // TODO applying a guess can take time to check constraints; do off main thread?
                        game.guess(solution)
                        view?.setGuess(solution, true)
                        advanceGame()
                    },
                    { cause ->
                        Timber.e(cause, "Error computing next game solution")
                        // TODO display error to user?
                    }
                )
        }
    }

    private fun advanceGameEvaluating() {
        if (gameSetup.evaluator == GameSetup.Evaluator.PLAYER) {
            view?.promptForEvaluation(game.currentGuess!!)
        } else {
            view?.promptForWait()
            disposable.dispose()
            disposable = computeEvaluation(game.currentGuess!!, game.constraints)
                .delay(3, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { constraint ->
                        game.evaluate(constraint)
                        view?.replaceGuessWithConstraint(constraint, true)
                        advanceGameCharacterEvaluations()
                    },
                    { cause ->
                        Timber.e(cause, "Error computing guess evaluation")
                        // TODO display error to user?
                    }
                )
        }
    }

    private fun advanceGameOver() {
        // TODO register game result with permanent record
        Timber.v("Game Over: ${game.state}. TODO: record result")
        if (gameSetup.evaluator == GameSetup.Evaluator.PLAYER) {
            val solved = game.state == Game.State.WON
            view?.showGameOver(
                solution = null,
                rounds = game.constraints.size,
                solved = solved,
                playerVictory = !solved || gameSetup.solver == GameSetup.Solver.PLAYER
            )
        } else {
            disposable.dispose()
            disposable = computePeek(game.constraints)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { solution ->
                        val solved = game.state == Game.State.WON
                        val playerWon = (gameSetup.solver == GameSetup.Solver.PLAYER && solved)
                                || (gameSetup.evaluator == GameSetup.Evaluator.PLAYER && !solved)
                        view?.showGameOver(
                            solution = solution,
                            rounds = game.constraints.size,
                            solved = solved,
                            playerVictory = playerWon
                        )
                    },
                    { cause ->
                        Timber.e(cause, "Error computing peeked solution")
                        // TODO display error to user?
                    }
                )
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Observable Helpers
    //---------------------------------------------------------------------------------------------
    private var cachedComputedCharEvaluations: Map<Char, CharacterEvaluation> = mapOf()
    private var cachedComputedCharEvaluationConstraints = listOf<Constraint>()

    private fun computeCharacterEvaluations(constraints: List<Constraint>): Single<Map<Char, CharacterEvaluation>> {
        // local copies so they don't change during computation
        val ccce: Map<Char, CharacterEvaluation>
        val cccec: List<Constraint>
        synchronized(this) {
            ccce = cachedComputedCharEvaluations
            cccec = cachedComputedCharEvaluationConstraints
        }

        return Single.defer {
            val map = if (ccce.isEmpty() || cccec.any { it !in constraints }) {
                val letters = gameSessionManager.getCodeCharacters(gameSetup)
                constrainCharacterEvaluations(initialCharacterEvaluations(letters), constraints)
            } else {
                constrainCharacterEvaluations(ccce, constraints.filter { it !in  cccec })
            }

            Timber.v("character evaluation map is $map")

            // update
            synchronized(this) {
                cachedComputedCharEvaluations = map
                cachedComputedCharEvaluationConstraints = constraints
            }

            Single.just(map)
        }.subscribeOn(Schedulers.computation())
    }

    private fun computeSolution(constraints: List<Constraint>): Single<String> {
        // Solver is available as a cached Single; compute it then use the result to determine
        // a solution for the provided game state.
        return Single.create { emitter ->
            val disposable = solverObservable.observeOn(Schedulers.computation())
                .subscribe(
                    { solver ->
                        val guess = solver.generateGuess(constraints)
                        if (solver is ModularSolver) {
                            Timber.v("Computed guess $guess from ${constraints.size} constraints, ${solver.candidates.guesses.size} possible guesses, ${solver.candidates.solutions.size} possible solutions")
                        }
                        emitter.onSuccess(guess)
                    },
                    { cause -> emitter.onError(cause) }
                )

            emitter.setCancellable { disposable.dispose() }
        }
    }

    private fun computeEvaluation(candidate: String, constraints: List<Constraint>): Single<Constraint> {
        // Evaluator is available as a cached Single; compute it then use the result to determine
        // an evaluation for the provided game state.
        return Single.create { emitter ->
            val disposable = evaluatorObservable.observeOn(Schedulers.computation())
                .subscribe(
                    { evaluator -> emitter.onSuccess(evaluator.evaluate(candidate, constraints)) },
                    { cause -> emitter.onError(cause) }
                )

            emitter.setCancellable { disposable.dispose() }
        }
    }

    private fun computePeek(constraints: List<Constraint>): Single<String> {
        // Evaluator is available as a cached Single; compute it then use the result to determine
        // a peeked solution for the provided game state.
        return Single.create { emitter ->
            val disposable = evaluatorObservable.observeOn(Schedulers.computation())
                .subscribe(
                    { evaluator -> emitter.onSuccess(evaluator.peek(constraints)) },
                    { cause -> emitter.onError(cause) }
                )

            emitter.setCancellable { disposable.dispose() }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Character Evaluations
    //---------------------------------------------------------------------------------------------
    private fun initialCharacterEvaluations(letters: Iterable<Char>) = letters
        .map { CharacterEvaluation(it, gameSetup.board.letters) }
        .associateBy { it.character }

    private fun constrainCharacterEvaluations(
        evaluations: Map<Char, CharacterEvaluation>,
        constraint: Constraint
    ): Map<Char, CharacterEvaluation> = constrainCharacterEvaluations(evaluations, listOf(constraint))

    private fun constrainCharacterEvaluations(
        evaluations: Map<Char, CharacterEvaluation>,
        constraints: List<Constraint>
    ): Map<Char, CharacterEvaluation> {
        val working = evaluations.toMutableMap()
        for (constraint in constraints) {
            val letters = constraint.candidate.toSet()
            for (letter in letters) {
                working[letter] = if (letter in working) {
                    working[letter]!!.constrained(constraint)
                } else {
                    CharacterEvaluation(
                        letter,
                        gameSetup.board.letters,
                        constraint.markup.filterIndexed { index, _ -> constraint.candidate[index] == letter }
                            .maxByOrNull { it.value() }
                    )
                }
            }
        }
        return working.toMap()
    }

    private fun CharacterEvaluation.constrained(constraint: Constraint): CharacterEvaluation {
        val characterMarkups = constraint.markup
            .filterIndexed { index, _ -> constraint.candidate[index] == character }

        Timber.v("characterMarkups for $character: $characterMarkups")
        val cMarkup = characterMarkups.maxByOrNull { it.value() }

        // maxCount is the minimum of:
        // 1. guess length minus number of included other characters in markup
        // 2. number of included markups IF a non-included example appears for the character

        val otherChars = constraint.markup
            .filterIndexed { index, markupType ->
                constraint.candidate[index] != character && markupType != Constraint.MarkupType.NO
            }.count()

        val sameChars = if (Constraint.MarkupType.NO !in characterMarkups) maxCount else characterMarkups.count {
            it != Constraint.MarkupType.NO
        }

        val cMaxCount = Math.min(sameChars, constraint.candidate.length - otherChars)

        return constrained(cMaxCount, cMarkup)
    }

    private fun CharacterEvaluation.constrained(maxCount: Int?, markup: Constraint.MarkupType?): CharacterEvaluation {
        val newMaxCount = Math.min(this.maxCount, maxCount ?: this.maxCount)
        val newMarkup = markup?.bestOf(this.markup) ?: this.markup

        return if (newMarkup == this.markup && newMaxCount == this.maxCount) this else {
            CharacterEvaluation(character, newMaxCount, newMarkup)
        }
    }

    private fun Constraint.MarkupType.value() = when(this) {
        Constraint.MarkupType.EXACT -> 2
        Constraint.MarkupType.INCLUDED -> 1
        Constraint.MarkupType.NO -> 0
    }

    private fun Constraint.MarkupType.bestOf(other: Constraint.MarkupType?): Constraint.MarkupType {
        return if (other == null || value() > other.value()) this else other
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}