package com.peaceray.codeword.presentation.presenters

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.manager.game.creation.GameCreationManager
import com.peaceray.codeword.data.manager.game.play.GamePlayManager
import com.peaceray.codeword.data.manager.game.play.GamePlaySession
import com.peaceray.codeword.data.manager.record.GameRecordManager
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.feedback.Feedback
import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackManager
import com.peaceray.codeword.presentation.manager.feedback.GameFeedbackProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * A Presenter for interactive Games. Uses a GamePlaySession to interact with a game and,
 * sometimes, its Evaluator and Solver.
 */
class GamePresenter @Inject constructor(): GameContract.Presenter, BasePresenter<GameContract.View>() {
    @Inject lateinit var gameCreationManager: GameCreationManager
    @Inject lateinit var gamePlayManager: GamePlayManager
    @Inject lateinit var gameRecordManager: GameRecordManager
    @Inject lateinit var gameFeedbackManager: GameFeedbackManager

    var gameSeed: String? = null
    lateinit var gameSetup: GameSetup
    private var ready = false
    private var forfeit = false
    private var cachedPartialGuess: String? = null

    private val locale: Locale = Locale.getDefault()

    private lateinit var gamePlaySession: GamePlaySession
    private lateinit var gameFeedbackProvider: GameFeedbackProvider
    private lateinit var gameFeedback: Feedback

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
        val locale = when (gameSetup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> gameSetup.vocabulary.language.locale!!
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> null
        }
        view?.setCodeType(
            gameCreationManager.getCodeCharacters(gameSetup),
            locale,
            gameSetup.evaluation.type
        )

        // create the GamePlaySession
        ready = false
        viewScope.launch {
            gamePlaySession = gamePlayManager.getGamePlaySession(gameSeed, gameSetup)
            gameFeedbackProvider = gameFeedbackManager.getGameFeedbackProvider(gameSetup, hints = false)
            gameFeedback = gameFeedbackProvider.getPlaceholderFeedback()

            clearConstraints()

            // if forfeit, apply immediately; otherwise prompt a user action
            if (forfeit) {
                recordGameOutcome(false)
            } else {
                // apply updated settings
                val update = view?.getUpdatedGameSetup()
                if (update != null) applyGameSetupUpdate(update)

                // begin game
                ready = true
                val moves = gamePlaySession.getCurrentMoves()
                setConstraints(moves.second.map { gameFeedbackProvider.toGuess(it, gameFeedback) })
                if (moves.first != null) {
                    setGuess(gameFeedbackProvider.toGuess(moves.first!!, gameFeedback))
                } else {
                    cachedPartialGuess = view?.getCachedGuess()
                    if ((cachedPartialGuess?.length?: 0) > 0) {
                        setGuess(gameFeedbackProvider.toGuess(cachedPartialGuess!!, gameFeedback))
                    }
                }
                advanceGameFeedback(false)
            }
        }
    }

    override fun onUpdatedGameSetup(gameSetup: GameSetup) {
        Timber.v("onUpdatedGameSetup")
        viewScope.launch { applyGameSetupUpdate(gameSetup) }
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
            viewScope.launch { recordGameOutcome(false) }
        }
    }

    override fun onGuessUpdated(before: String, after: String) {
        Timber.v("onGuessUpdate for characters $before -> $after")
        viewScope.launch {
            // all codes lowercase
            val charset = gameCreationManager.getCodeCharacters(gameSetup)
            val sanitized = after.lowercase(locale)
            Timber.v("onGuessUpdated: have charset $charset sanitized '$sanitized'")

            val ok = isReadyForPlayerGuess()
                    && after.length <= gameSetup.vocabulary.length
                    && sanitized.all { it in charset }
            Timber.v("onGuessUpdated: ok $ok isReady ${isReadyForPlayerGuess()}")

            val isReadyOk = isReadyForPlayerGuess()
            val isLengthOk = after.length <= gameSetup.vocabulary.length
            val isSanitized = sanitized.all { it in charset }
            Timber.d("onGuessUpdated: isReadyOk $isReadyOk isLengthOk $isLengthOk isSanitized $isSanitized")

            if (ok) setGuess(gameFeedbackProvider.toGuess(sanitized, gameFeedback))
        }
    }

    override fun onGuess(guess: String) {
        Timber.v("onGuess $guess")
        viewScope.launch {
            if (isReadyForPlayerGuess()) {
                val sanitizedGuess = guess.lowercase(locale).trim()
                try {
                    gamePlaySession.advanceWithGuess(sanitizedGuess)
                    advanceGame()
                } catch (err: Game.IllegalGuessException) {
                    Timber.e(err, "Couldn't apply guess")
                    reportGuessError(sanitizedGuess, err)
                }
            }
        }
    }

    override fun onEvaluation(guess: String, markup: List<Constraint.MarkupType>) {
        Timber.v("onEvaluation $guess $markup")
        viewScope.launch {
            if (isReadyForPlayerEvaluation()) {
                val constraint = Constraint.create(guess.lowercase(locale).trim(), markup)
                try{
                    gamePlaySession.advanceWithEvaluation(constraint)
                    replaceGuessWithConstraints(gameFeedbackProvider.toGuess(constraint, gameFeedback))
                    advanceGame()
                } catch (err: Game.IllegalEvaluationException) {
                    Timber.e(err, "Error evaluating guess $guess")
                    when(err.error) {
                        Game.EvaluationError.GUESS -> view?.showError(GameContract.ErrorType.EVALUATION_INCONSISTENT)
                        else -> TODO("Unknown Error")
                    }
                }
            }
        }
    }

    override fun onEvaluation(guess: String, exact: Int, included: Int) {
        TODO("Not yet implemented")
    }

    //region Game State
    //---------------------------------------------------------------------------------------------
    private suspend fun isReadyForPlayerGuess(): Boolean {
        return ready && !forfeit && gameSetup.solver == GameSetup.Solver.PLAYER
                && gamePlaySession.getGameState() == Game.State.GUESSING
    }

    private suspend fun isReadyForPlayerEvaluation(): Boolean {
        return ready && !forfeit && gameSetup.evaluator == GameSetup.Evaluator.PLAYER
                && gamePlaySession.getGameState() == Game.State.EVALUATING
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Game Progression
    //---------------------------------------------------------------------------------------------
    private val saveScope = CoroutineScope(Dispatchers.Main)
    private var saveJob: Job? = null
    private fun saveGame() {
        saveJob?.cancel("A new saved game is being prepared")
        saveJob = saveScope.launch {
            Timber.v("Saving the game...")
           gamePlaySession.save()
            Timber.v("...Saved!")
        }
    }

    private suspend fun advanceGame(save: Boolean = true) {
        Timber.v("advanceGame")

        if (save) saveGame()
        advanceGameWithoutSaving()
    }

    private suspend fun advanceGameWithoutSaving() {
        when (gamePlaySession.getGameState()) {
            Game.State.GUESSING -> advanceGameGuessing()
            Game.State.EVALUATING -> advanceGameEvaluating()
            Game.State.WON, Game.State.LOST -> advanceGameOver()
        }
    }

    private var guessFlowJob: Job? = null
    private suspend fun advanceGameFeedback(saveAfter: Boolean) {
        guessFlowJob?.cancel("Advancing game feedback for new Constraints")
        // TODO better scope management, so new Constraints halt this process
        val constraints = gamePlaySession.getConstraints()
        guessFlowJob = viewScope.launch {
            gameFeedback = gameFeedbackProvider.getFeedback(constraints)
            view?.setCharacterFeedback(gameFeedback.characters)


            // advance game: will set a partial guess ("") with this feedback
            advanceGame(saveAfter)

            // update existing Constraints for the new feedback; impose delays so the entire game
            // field doesn't update all at once.
            delay(80L)
            gameFeedbackProvider.toGuessesFlow(gamePlaySession.getConstraints(), gameFeedback, true)
                .collect { (index, guess) ->
                    if (updateConstraint(index, guess)) delay(80L)
                }
        }
    }

    private suspend fun advanceGameGuessing() {
        Timber.v("advanceGameGuessing")
        if (gameSetup.solver == GameSetup.Solver.PLAYER) {
            view?.promptForGuess(gameFeedbackProvider.toGuess(cachedPartialGuess ?: "", gameFeedback))
            cachedPartialGuess = null
        } else {
            val time = System.currentTimeMillis()
            view?.promptForWait()
            try {
                val solution = gamePlaySession.generateGuess(true)
                Timber.v("Computed and applied a solution: $solution. Took ${(System.currentTimeMillis() - time) / 1000.0} seconds")
                setGuess(gameFeedbackProvider.toGuess(solution, gameFeedback))
                advanceGame()
            } catch (err: IllegalStateException) {
                Timber.e("GamePlaySession generated a guess, but Game was not accepting guesses")
            } catch (err: Game.IllegalGuessException) {
                Timber.e(err, "GamePlaySession generated a guess, but could not legally apply it")
            } catch (err: UnsupportedOperationException) {
                Timber.e(err, "Attempted to generate a guess, but gamePlaySession cannot")
            }
        }
    }

    private suspend fun advanceGameEvaluating() {
        Timber.v("advanceGameEvaluating")
        if (gameSetup.evaluator == GameSetup.Evaluator.PLAYER) {
            cachedPartialGuess = null
            view?.promptForEvaluation(gameFeedbackProvider.toGuess(gamePlaySession.getCurrentGuess()!!, gameFeedback))
        } else {
            view?.promptForWait()
            try {
                val constraint = gamePlaySession.generateEvaluation(true)
                Timber.v("Computed and applied an evaluation: $constraint.")
                replaceGuessWithConstraints(gameFeedbackProvider.toGuess(constraint, gameFeedback))
                advanceGameFeedback(true)
            } catch (err: IllegalStateException) {
                Timber.e("GamePlaySession generated a Constraint, but Game was not accepting evaluation")
            } catch (err: Game.IllegalEvaluationException) {
                Timber.e(err, "GamePlaySession generated a Constraint, but could not legally apply it")
            } catch (err: UnsupportedOperationException) {
                Timber.e(err, "Attempted to generate a Constraint, but gamePlaySession cannot")
            }
        }
    }

    private suspend fun advanceGameOver() {
        Timber.v("Game Over")
        // no longer ready to receive moves
        ready = false
        // register game result with permanent record.
        // should be done before notifying presenter.
        recordGameOutcome(true)
    }

    private suspend fun applyGameSetupUpdate(updatedGameSetup: GameSetup) {
        if (gameSetup != updatedGameSetup && gamePlayManager.canUpdateGamePlaySession(gamePlaySession, updatedGameSetup)) {
            gamePlaySession = gamePlayManager.getUpdatedGamePlaySession(gamePlaySession, updatedGameSetup)
            gameSetup = updatedGameSetup

            // set board size
            if (gameSetup.board.rounds == 0) {
                view?.setGameFieldUnlimited(gameSetup.vocabulary.length)
            } else {
                view?.setGameFieldSize(gameSetup.vocabulary.length, gameSetup.board.rounds)
            }
            saveGame()
        }
    }

    private suspend fun recordGameOutcome(showOutcome: Boolean) {
        // record via a save (might be a forfeit or end-of-game)
        val save = gamePlaySession.getGameSaveData()
        val solution = if (!gamePlaySession.canGenerateSolutions) null else gamePlaySession.generateSolution()

        // record for the records
        gameRecordManager.record(save, solution)

        // display to the view
        if (showOutcome) {
            val state = gamePlaySession.getGameState()
            val constraints = gamePlaySession.getConstraints()
            val solved = state == Game.State.WON
            val playerWon = (gameSetup.solver == GameSetup.Solver.PLAYER && solved)
                    || (gameSetup.evaluator == GameSetup.Evaluator.PLAYER && !solved)
            view?.showGameOver(
                save.uuid,
                solution = solution,
                rounds = constraints.size,
                solved = solved,
                playerVictory = playerWon
            )
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Guess Updates
    //---------------------------------------------------------------------------------------------

    private val _constraints: MutableList<Guess> = mutableListOf()
    private var _guess: Guess? = null

    private fun clearConstraints(): Boolean {
        val changed = _constraints.isNotEmpty() || _guess != null
        _constraints.clear()
        _guess = null
        // no view update; cannot set null guess
        return changed
    }

    private fun setConstraints(constraints: List<Guess>): Boolean {
        val changed = _constraints.size != constraints.size
                || _constraints.zip(constraints).any { it.first != it.second }
        _constraints.clear()
        _constraints.addAll(constraints)
        if (changed) view?.setConstraints(constraints, true)
        return changed
    }

    private fun setGuess(guess: Guess): Boolean {
        val changed = _guess != guess
        _guess = guess
        if (changed) view?.setGuess(guess, true)
        return changed
    }

    private fun replaceGuessWithConstraints(constraint: Guess): Boolean {
        _constraints.add(constraint)
        _guess = null
        view?.replaceGuessWithConstraint(constraint, true)
        return true
    }

    private fun updateConstraint(index: Int, constraint: Guess): Boolean {
        val changed = index < _constraints.size && _constraints[index] != constraint
        if (changed) view?.updateConstraint(index, constraint, true)
        return changed
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Error Handling
    //---------------------------------------------------------------------------------------------
    private fun reportGuessError(guess: String, err: Game.IllegalGuessException) {
        var violations = err.violations
        val type = when(err.error) {
            Game.GuessError.LENGTH ->
                if (guess.isEmpty()) GameContract.ErrorType.GUESS_EMPTY else GameContract.ErrorType.GUESS_LENGTH
            Game.GuessError.VALIDATION -> {
                // check for letter repetitions; this error type is not labeled by the Game
                // as a Violation.
                val repeatedLetter = guess.toSet().associateWith { char -> guess.count { char == it } }.maxByOrNull { it.value }
                if (repeatedLetter != null && repeatedLetter.value > gameSetup.vocabulary.characterOccurrences) {
                    violations = listOf(Constraint.Violation(
                        Constraint.create(guess, guess),
                        guess,
                        guess.indexOf(repeatedLetter.key)
                    ))
                    GameContract.ErrorType.GUESS_LETTER_REPETITIONS
                } else {
                    GameContract.ErrorType.GUESS_INVALID
                }
            }
            Game.GuessError.CONSTRAINTS -> GameContract.ErrorType.GUESS_NOT_CONSTRAINED
            else -> GameContract.ErrorType.UNKNOWN
        }
        view?.showError(type, violations)
    }
    //---------------------------------------------------------------------------------------------
    //endregion
}