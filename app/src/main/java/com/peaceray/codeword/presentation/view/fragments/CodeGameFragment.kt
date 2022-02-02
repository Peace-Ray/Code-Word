package com.peaceray.codeword.presentation.view.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.databinding.FragmentCodeGameBinding
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.CodeGameContract
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import com.peaceray.codeword.presentation.view.component.adapters.GuessAdapter
import com.peaceray.codeword.presentation.view.component.views.CodeKeyboardView
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class CodeGameFragment: Fragment(R.layout.fragment_code_game), CodeGameContract.View {

    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: FragmentCodeGameBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var guessAdapter: GuessAdapter
    lateinit var guessLayoutManager: RecyclerView.LayoutManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCodeGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // view configuration
        guessLayoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        binding.constraintRecyclerView.adapter = guessAdapter
        binding.constraintRecyclerView.layoutManager = guessLayoutManager

        binding.keyboardContainer.keyboardView.onKeyListener = object: CodeKeyboardView.OnKeyListener {
            override fun onCharacter(character: Char) {
                Timber.v("onCharacter $character")
                val guess = guessAdapter.guess?.candidate ?: ""
                presenter.onGuessUpdated(guess, "$guess$character")
            }

            override fun onEnter() {
                val guess = guessAdapter.guess?.candidate ?: ""
                presenter.onGuess(guess)
            }

            override fun onDelete() {
                val guess = guessAdapter.guess?.candidate ?: ""
                if (guess.isNotEmpty()) {
                    presenter.onGuessUpdated(guess, guess.dropLast(1))
                }
            }
        }

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: CodeGameContract.Presenter

    override fun setGameFieldSize(length: Int, rows: Int) {
        Timber.v("setGameFieldSize: $length $rows")
        guessAdapter.setGameFieldSize(length, rows)
    }

    override fun setGameFieldUnlimited(length: Int) {
        Timber.v("setGameFieldUnlimited: $length")
        guessAdapter.setGameFieldSize(length, 0)
    }

    override fun setCodeLanguage(characters: Iterable<Char>, locale: Locale) {
        TODO("Not yet implemented")
    }

    override fun setCodeComposition(characters: Iterable<Char>) {
        TODO("Not yet implemented")
    }

    override fun setConstraints(constraints: List<Constraint>, animate: Boolean) {
        Timber.v("setConstraints: ${constraints.size}")
        // TODO deal with [animate]
        guessAdapter.setConstraints(constraints)
    }

    override fun setGuess(guess: String, animate: Boolean) {
        Timber.v("setGuess: $guess")
        // TODO deal with [animate]
        guessAdapter.replaceGuess(guess = guess)
    }

    override fun replaceGuessWithConstraint(constraint: Constraint, animate: Boolean) {
        Timber.v("replaceGuessWithConstraint: $constraint")
        // TODO deal with [animate]
        guessAdapter.replaceGuess(constraint = constraint)
    }

    override fun setCharacterEvaluations(evaluations: Map<Char, CharacterEvaluation>) {
        Timber.v("setCharacterEvaluations ${evaluations.size}")
        binding.keyboardContainer.keyboardView.setCharacterEvaluations(evaluations)
    }

    override fun promptForGuess() {
        Timber.v("promptForGuess")
        // clear "guess" field or placeholder
        guessAdapter.replaceGuess(guess = "")

        // TODO disable evaluation
        // TODO enable guessing
    }

    override fun promptForEvaluation(guess: String) {
        Timber.v("promptForEvaluation $guess")
        guessAdapter.replaceGuess(guess = guess)

        // TODO disable guessing
        // TODO enable evaluation
    }

    override fun promptForWait() {
        Timber.v("promptForWait")
        // TODO disable guessing
        // TODO disable evaluation
        // TODO show a "please wait" indicator
    }

    override fun showGameOver(
        solution: String?,
        rounds: Int,
        solved: Boolean,
        playerVictory: Boolean
    ) {
        Timber.v("showGameOver ($solution ${if (solved) "found" else "not found"} in $rounds) Player Victory: $playerVictory")
        // TODO actually show a real Game Over screen

        val playerString = if (playerVictory) "You Win!" else "Game Over"
        val toastString = when {
            solved -> "$playerString\n\"$solution\" guessed in $rounds rounds."
            solution != null -> "$playerString\n\"$solution\" not guessed."
            else -> "$playerString\nCode word wasn't guessed in $rounds rounds"
        }

        Toast.makeText(context, toastString, Toast.LENGTH_LONG).show()
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}