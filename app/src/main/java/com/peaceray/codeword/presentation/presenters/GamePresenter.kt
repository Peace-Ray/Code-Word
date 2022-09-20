package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.manager.game.GameSessionManager
import com.peaceray.codeword.domain.manager.record.GameRecordManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.Solver
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.ModularSolver
import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import com.peaceray.codeword.utils.wrappers.WrappedNullable
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
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
class GamePresenter @Inject constructor(): GameContract.Presenter, BasePresenter<GameContract.View>() {
    @Inject lateinit var gameSessionManager: GameSessionManager
    @Inject lateinit var gameRecordManager: GameRecordManager

    var gameSeed: String? = null
    lateinit var gameSetup: GameSetup
    lateinit var game: Game
    private var ready = false
    private var forfeit = false
    private var cachedPartialGuess: String? = null
    private val readyForPlayerGuess
        get() = ready && !forfeit && game.state == Game.State.GUESSING && gameSetup.solver == GameSetup.Solver.PLAYER
    private val readyForPlayerEvaluation
        get() = ready && !forfeit && game.state == Game.State.EVALUATING && gameSetup.evaluator == GameSetup.Evaluator.PLAYER

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

        // retrieve GameSetup from the View
        gameSeed = view!!.getGameSeed()
        gameSetup = view!!.getGameSetup()

        // set board size
        if (gameSetup.board.rounds == 0) {
            view?.setGameFieldUnlimited(gameSetup.vocabulary.length)
        } else {
            view?.setGameFieldSize(gameSetup.vocabulary.length, gameSetup.board.rounds)
        }
        when (gameSetup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> {
                view?.setCodeLanguage(
                    gameSessionManager.getCodeCharacters(gameSetup),
                    gameSetup.vocabulary.language.locale!!
                )
            }
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> {
                view?.setCodeComposition(gameSessionManager.getCodeCharacters(gameSetup))
            }
        }

        // preload Solver and Evaluator (as needed)
        if (gameSetup.solver != GameSetup.Solver.PLAYER) {
            solverObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe { solver -> Timber.v("Preloaded Solver $solver") }
        }

        if (gameSetup.evaluator != GameSetup.Evaluator.PLAYER) {
            evaluatorObservable.observeOn(AndroidSchedulers.mainThread())
                .subscribe { evaluator -> Timber.v("Preloaded Evaluator $evaluator peeked ${evaluator.peek(listOf())}") }
        }

        // load (or create) the game, provide the lateinit field, then set char evaluations
        ready = false
        Single.defer { Single.just(gameSessionManager.getGame(gameSeed, gameSetup)) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { game ->
                    Timber.v("Game loaded at round ${game.round} with currentGuess ${game.currentGuess}")
                    this.game = game

                    // if forfeit, apply immediately; otherwise prompt a user action
                    if (forfeit) {
                        recordGameOutcome(false)
                    } else {
                        // apply updated settings
                        val update = view?.getUpdatedGameSetup()
                        if (update != null) applyGameSetupUpdate(update)

                        // begin game
                        this.ready = true
                        view?.setConstraints(game.constraints, true)
                        if (game.currentGuess != null) {
                            view?.setGuess(game.currentGuess!!, true)
                        } else {
                            cachedPartialGuess = view?.getCachedGuess()
                            if ((cachedPartialGuess?.length ?: 0) > 0) view?.setGuess(cachedPartialGuess!!, true)
                        }
                        advanceGameCharacterEvaluations(false)
                    }
                },
                { error ->
                    Timber.e(error, "Error loading game session; attempting a fresh start")
                    this.game = gameSessionManager.getGame(gameSeed, gameSetup, create = true)
                    this.ready = true
                    view?.setConstraints(game.constraints, true)
                    if (game.currentGuess != null) view?.setGuess(game.currentGuess!!, true)
                    advanceGameCharacterEvaluations(false)
                }
            )
    }

    override fun onDetached() {
        super.onDetached()

        disposable.dispose()
    }

    override fun onUpdatedGameSetup(gameSetup: GameSetup) {
        Timber.v("onUpdatedGameSetup")
        applyGameSetupUpdate(gameSetup)
    }

    override fun onForfeit() {
        // forfeits are potentially generated outside of the Contract, meaning they may be input
        // before the game is loaded and ready. In such a case, note that a forfeit has occurred;
        // it is applied once the game is loaded.
        // (note a potential race condition here, which is resolved under the assumption that
        // the View layer calls this function only from the Android main thread).
        forfeit = true
        if (ready) {
            ready = false
            recordGameOutcome(false)
        }
    }

    override fun onGuessUpdated(before: String, after: String) {
        Timber.v("onGuessUpdate for characters $before -> $after")
        // all codes lowercase
        val charset = gameSessionManager.getCodeCharacters(gameSetup)
        val ok = readyForPlayerGuess
                && after.length <= gameSetup.vocabulary.length
                && after.toLowerCase(locale).all { it in charset }
        if (ok) view?.setGuess(after.toLowerCase(locale))
    }

    override fun onGuess(guess: String) {
        if (!readyForPlayerGuess) return

        // convention: all codes lowercase
        val sanitizedGuess = guess.toLowerCase(locale).trim()
        try {
            // TODO applying a guess can take time to check constraints; do off main thread?
            game.guess(sanitizedGuess)
            advanceGame()
        } catch (err: Game.IllegalGuessException) {
            Timber.e(err, "Couldn't apply guess")
            reportGuessError(sanitizedGuess, err)
        }
    }

    override fun onEvaluation(guess: String, markup: List<Constraint.MarkupType>) {
        if (!readyForPlayerEvaluation) return
        try {
            // convention: all codes lowercase
            val constraint = Constraint.create(guess.toLowerCase(locale), markup)
            game.evaluate(constraint)
            view?.replaceGuessWithConstraint(constraint)
            advanceGame()
        } catch (err: Game.IllegalEvaluationException) {
            Timber.e(err, "Error evaluating guess $guess with current game guess ${game.currentGuess}")
            when(err.error) {
                Game.EvaluationError.GUESS -> view?.showError(GameContract.ErrorType.EVALUATION_INCONSISTENT)
                else -> TODO("Unknown Error")
            }
        }
    }

    override fun onEvaluation(guess: String, exact: Int, included: Int) {
        TODO("Not yet implemented")
    }

    //region Game Progression
    //---------------------------------------------------------------------------------------------
    private var saveDisposable = Disposable.disposed()

    private fun advanceGame(save: Boolean = true) {
        Timber.v("advanceGame")

        if (save) {
            Timber.v("Saving the game...")
            val saveData = GameSaveData(gameSeed, gameSetup, game)
            saveDisposable.dispose()
            saveDisposable = Completable.fromAction { gameSessionManager.saveGame(saveData) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { Timber.v("Saved!") },
                    { error -> Timber.e(error, "An error occurred saving the game") }
                )
        }
        advanceGameWithoutSaving()
    }

    private fun advanceGameWithoutSaving() {
        Timber.v("advanceGameWithoutSaving")
        when (game.state) {
            Game.State.GUESSING -> advanceGameGuessing()
            Game.State.EVALUATING -> advanceGameEvaluating()
            Game.State.WON, Game.State.LOST -> advanceGameOver()
        }
    }

    private fun advanceGameCharacterEvaluations(saveAfter: Boolean) {
        disposable.dispose()
        disposable = computeCharacterEvaluations(game.constraints)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { solution ->
                    view?.setCharacterEvaluations(solution)
                    advanceGame(saveAfter)
                },
                { cause ->
                    Timber.e(cause, "Error computing character constraints")
                    // TODO display error to user?
                }
            )
    }

    private fun advanceGameGuessing() {
        if (gameSetup.solver == GameSetup.Solver.PLAYER) {
            view?.promptForGuess(cachedPartialGuess)
            cachedPartialGuess = null
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
            cachedPartialGuess = null
        } else {
            view?.promptForWait()
            disposable.dispose()
            disposable = computeEvaluation(game.currentGuess!!, game.constraints)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { constraint ->
                        game.evaluate(constraint)
                        view?.replaceGuessWithConstraint(constraint, true)
                        advanceGameCharacterEvaluations(true)

                        // TODO remove
                        computePeek(game.constraints)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(
                                { secret -> Timber.d("Peeked secret is $secret") },
                                { error -> Timber.e(error, "An error occurred peeking for a solution") }
                            )
                    },
                    { cause ->
                        Timber.e(cause, "Error computing guess evaluation")
                        // TODO display error to user?
                    }
                )
        }
    }

    private fun advanceGameOver() {
        Timber.v("Game Over: ${game.state}")
        // no longer ready to receive moves
        ready = false
        // register game result with permanent record.
        // should be done before notifying presenter.
        recordGameOutcome(true)
    }

    private fun applyGameSetupUpdate(updatedGameSetup: GameSetup) {
        if (gameSetup != updatedGameSetup) {
            val settings = gameSessionManager.getSettings(updatedGameSetup)
            if (game.canUpdateSettings(settings)) {
                Timber.v("Updating game settings")
                game.updateSettings(settings)
                gameSetup = updatedGameSetup

                Timber.v("Updating new game settings to view")
                // set board size
                if (gameSetup.board.rounds == 0) {
                    view?.setGameFieldUnlimited(gameSetup.vocabulary.length)
                } else {
                    view?.setGameFieldSize(gameSetup.vocabulary.length, gameSetup.board.rounds)
                }

                Timber.v("Saving the game...")
                val save = GameSaveData(gameSeed, gameSetup, game)
                saveDisposable.dispose()
                saveDisposable = Completable.fromAction { gameSessionManager.saveGame(save) }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                        { Timber.v("Saved!") },
                        { error -> Timber.e(error, "An error occurred saving the game") }
                    )
            }
        }
    }

    private fun recordGameOutcome(showOutcome: Boolean) {
        // record via a save (might be a forfeit or end-of-game)
        val save = GameSaveData(gameSeed, gameSetup, game)

        if (!showOutcome) {
            // simple; record in the background, don't notify View
            computePeekWrappedNullable(save.constraints)
                .subscribeOn(Schedulers.io())
                .subscribe( // stay on IO thread for database update
                    { wrappedSecret ->
                        Timber.v("recording the game result with secret ${wrappedSecret.value}")
                        gameRecordManager.record(save, wrappedSecret.value)
                        Timber.v("recorded")
                    },
                    { error -> Timber.e(error, "An error occurred computing game solution for a forfeit") }
                )
        } else {
            // calculate secret (if applicable), record the result, then inform the view -- passing
            // along the calculated secret. This operation is not disposable; even if the view
            // becomes detached, the outcome must still be recorded.
            Single.create<WrappedNullable<String>> { emitter ->
                computePeekWrappedNullable(save.constraints)
                    .subscribeOn(Schedulers.computation())
                    .observeOn(Schedulers.io())
                    .subscribe(
                        { solution ->
                            gameRecordManager.record(save, solution.value)
                            emitter.onSuccess(solution)
                        },
                        { cause -> emitter.onError(cause) }
                    )
            }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { solution ->
                        val solved = game.state == Game.State.WON
                        val playerWon = (gameSetup.solver == GameSetup.Solver.PLAYER && solved)
                                || (gameSetup.evaluator == GameSetup.Evaluator.PLAYER && !solved)
                        view?.showGameOver(
                            save.uuid,
                            solution = solution.value,
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

        // secret may or may not be known

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


    private fun computePeekWrappedNullable(constraints: List<Constraint>): Single<WrappedNullable<String>> {
        // When solved by the Player, no solution will be available
        return if (gameSetup.evaluator == GameSetup.Evaluator.PLAYER) Single.just(WrappedNullable()) else {
            computePeek(constraints).map { WrappedNullable(it) }
        }
    }

    private fun computePeek(constraints: List<Constraint>): Single<String> {
        // If a Constraint is marked correct, just report that candidate guess. Otherwise,
        // the Evaluator is available as a cached Single; compute it then use the result to determine
        // a peeked solution for the provided game state.
        return Single.create { emitter ->
            val lastConstraint = constraints.lastOrNull()
            if (lastConstraint != null && lastConstraint.correct) {
                emitter.onSuccess(lastConstraint.candidate)
            } else {
                val disposable = evaluatorObservable.observeOn(Schedulers.computation())
                    .subscribe(
                        { evaluator -> emitter.onSuccess(evaluator.peek(constraints)) },
                        { cause -> emitter.onError(cause) }
                    )

                emitter.setCancellable { disposable.dispose() }
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Character Evaluations
    //---------------------------------------------------------------------------------------------
    private fun initialCharacterEvaluations(letters: Iterable<Char>) = letters
        .map { CharacterEvaluation(it, gameSetup.vocabulary.length) }
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
                        gameSetup.vocabulary.length,
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

    //region Error Handling
    //---------------------------------------------------------------------------------------------
    private fun reportGuessError(guess: String, err: Game.IllegalGuessException) {
        val type = when(err.error) {
            Game.GuessError.LENGTH ->
                if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
                    if (guess.isEmpty()) GameContract.ErrorType.WORD_EMPTY else GameContract.ErrorType.WORD_LENGTH
                } else {
                    if (guess.isEmpty()) GameContract.ErrorType.CODE_EMPTY else GameContract.ErrorType.CODE_LENGTH
                }
            Game.GuessError.VALIDATION ->
                if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
                    GameContract.ErrorType.WORD_NOT_RECOGNIZED
                } else {
                    GameContract.ErrorType.CODE_INVALID
                }
            Game.GuessError.CONSTRAINTS ->
                if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
                    GameContract.ErrorType.WORD_NOT_CONSTRAINED
                } else {
                    GameContract.ErrorType.CODE_NOT_CONSTRAINED
                }
            else -> GameContract.ErrorType.UNKNOWN
        }
        view?.showError(type, err.violations)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}