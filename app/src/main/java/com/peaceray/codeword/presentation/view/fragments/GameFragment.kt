package com.peaceray.codeword.presentation.view.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.FragmentGameBinding
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.datamodel.CharacterEvaluation
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.adapters.GuessLetterAdapter
import com.peaceray.codeword.presentation.view.component.viewholders.GuessLetterViewHolder
import com.peaceray.codeword.presentation.view.component.views.CodeKeyboardView
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class GameFragment: Fragment(R.layout.fragment_game), GameContract.View {

    //region Creation, Arguments, Listener, External Actions
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameFragment"
        const val ARG_GAME_SEED = "${NAME}_GAME_SEED"
        const val ARG_GAME_SETUP = "${NAME}_GAME_SETUP"
        const val ARG_GAME_SETUP_UPDATE = "${NAME}_GAME_SETUP_UPDATE"

        fun newInstance(seed: String?, gameSetup: GameSetup, gameSetupUpdate: GameSetup? = null): GameFragment {
            val fragment = GameFragment()

            val args = Bundle()
            args.putString(ARG_GAME_SEED, seed)
            args.putParcelable(ARG_GAME_SETUP, gameSetup)
            args.putParcelable(ARG_GAME_SETUP_UPDATE, gameSetupUpdate ?: gameSetup)

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onGameOver(
            fragment: GameFragment,
            seed: String?,
            gameSetup: GameSetup,
            solution: String?,
            rounds: Int,
            solved: Boolean,
            playerVictory: Boolean
        )
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var guessAdapter: GuessLetterAdapter
    lateinit var guessLayoutManager: RecyclerView.LayoutManager

    @Inject lateinit var colorSwatchManager: ColorSwatchManager

    var listener: OnInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is GameSetupFragment.OnInteractionListener -> parentFragment as OnInteractionListener
            context is GameFragment.OnInteractionListener -> context
            else -> throw RuntimeException(
                "${parentFragment.toString()} or ${context}"
                        + " must implement ${javaClass.simpleName}.OnInteractionListener"
            )
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // view behavior
        binding.constraintRecyclerView.adapter = guessAdapter
        binding.constraintRecyclerView.itemAnimator = GuessLetterViewHolder.ItemAnimator()
        onRecyclerViewContentSizeChange()

        binding.keyboardContainer.keyboardView.isEnabled = false
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

        // view colors
        colorSwatchManager.colorSwatchLiveData.observe(viewLifecycleOwner) { updateViewColors(it) }

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region View Helpers
    //---------------------------------------------------------------------------------------------
    private var toast: Toast? = null
    private var recyclerViewScrolling: Boolean? = null

    private fun displayError(message: CharSequence) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private fun displayGuessError(message: CharSequence) {
        displayError(message)
        // rumble guess
        val length = guessAdapter.guess?.candidate?.length ?: 0
        guessAdapter.guessItemRange(placeholders = length == 0)
            .map { binding.constraintRecyclerView.findViewHolderForAdapterPosition(it) }
            .forEach { if (it is GuessLetterViewHolder) it.rumble() }
    }

    private fun displayEvaluationError(message: CharSequence) {
        displayError(message)
        // TODO rumble the guess?
    }

    private fun updateViewColors(swatch: ColorSwatch) {
        binding.mainView.setBackgroundColor(swatch.container.background)
        binding.constraintRecyclerView.setBackgroundColor(swatch.container.background)
        binding.keyboardContainer.keyboardView.setBackgroundColor(swatch.container.background)
    }

    private fun onRecyclerViewContentSizeChange() {
        binding.constraintRecyclerView.post {
            val height = binding.constraintRecyclerView.computeVerticalScrollRange()
            val scrolling = height > binding.constraintRecyclerView.height
            if (scrolling != recyclerViewScrolling) {
                // raise or lower keyboard
                binding.keyboardContainer.keyboardView.animate()
                    .z(if (scrolling) resources.getDimension(R.dimen.keyboard_elevation) else 0.0f)
                    .setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                    .start()

                recyclerViewScrolling = scrolling
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameContract.Presenter

    override fun getGameSeed(): String? {
        return requireArguments().getString(ARG_GAME_SEED)
    }

    override fun getGameSetup(): GameSetup {
        return requireArguments().getParcelable(ARG_GAME_SETUP)!!
    }

    override fun getUpdatedGameSetup(): GameSetup {
        return requireArguments().getParcelable(ARG_GAME_SETUP_UPDATE)!!
    }

    override fun setGameFieldSize(length: Int, rows: Int) {
        Timber.v("setGameFieldSize: $length $rows")
        guessLayoutManager = GridLayoutManager(context, length)
        binding.constraintRecyclerView.layoutManager = guessLayoutManager

        guessAdapter.setGameFieldSize(length, rows)
        onRecyclerViewContentSizeChange()
    }

    override fun setGameFieldUnlimited(length: Int) {
        Timber.v("setGameFieldUnlimited: $length")
        guessLayoutManager = GridLayoutManager(context, length)
        binding.constraintRecyclerView.layoutManager = guessLayoutManager

        guessAdapter.setGameFieldSize(length, 0)
        onRecyclerViewContentSizeChange()
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

        val positionRange = guessAdapter.guessItemRange(true)
        binding.constraintRecyclerView.postDelayed({
            binding.constraintRecyclerView.smoothScrollToPosition(positionRange.last)
        }, 100)

        onRecyclerViewContentSizeChange()
    }

    override fun setGuess(guess: String, animate: Boolean) {
        Timber.v("setGuess: $guess")
        // TODO deal with [animate]
        guessAdapter.replaceGuess(guess = guess)

        val positionRange = guessAdapter.guessItemRange(true)
        binding.constraintRecyclerView.postDelayed({
            binding.constraintRecyclerView.smoothScrollToPosition(positionRange.last)
        }, 100)
    }

    override fun replaceGuessWithConstraint(constraint: Constraint, animate: Boolean) {
        Timber.v("replaceGuessWithConstraint: $constraint")
        // TODO deal with [animate]
        guessAdapter.replaceGuess(constraint = constraint)

        val positionRange = guessAdapter.guessItemRange(true)
        binding.constraintRecyclerView.postDelayed({
            binding.constraintRecyclerView.smoothScrollToPosition(positionRange.last)
        }, 100)

        onRecyclerViewContentSizeChange()
    }

    override fun setCharacterEvaluations(evaluations: Map<Char, CharacterEvaluation>) {
        Timber.v("setCharacterEvaluations ${evaluations.size}")
        binding.keyboardContainer.keyboardView.setCharacterEvaluations(evaluations)
    }

    override fun promptForGuess() {
        Timber.v("promptForGuess")
        // clear "guess" field or placeholder
        guessAdapter.replaceGuess(guess = "")

        binding.keyboardContainer.keyboardView.isEnabled = true
    }

    override fun promptForEvaluation(guess: String) {
        Timber.v("promptForEvaluation $guess")
        guessAdapter.replaceGuess(guess = guess)

        binding.keyboardContainer.keyboardView.isEnabled = false
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

        binding.keyboardContainer.keyboardView.isEnabled = false

        listener?.onGameOver(
            this,
            requireArguments().getString(ARG_GAME_SEED),
            requireArguments().getParcelable(ARG_GAME_SETUP)!!,
            solution,
            rounds,
            solved,
            playerVictory
        )

        val playerString = if (playerVictory) "You Win!" else "Game Over"
        val toastString = when {
            solved -> "$playerString\n\"$solution\" guessed in $rounds rounds."
            solution != null -> "$playerString\n\"$solution\" not guessed."
            else -> "$playerString\nCode word wasn't guessed in $rounds rounds"
        }

        Toast.makeText(context, toastString, Toast.LENGTH_LONG).show()
    }

    override fun showError(error: GameContract.ErrorType, violations: List<Constraint.Violation>?) {
        val word = when (error) {
            GameContract.ErrorType.CODE_EMPTY,
            GameContract.ErrorType.CODE_LENGTH,
            GameContract.ErrorType.CODE_INVALID,
            GameContract.ErrorType.CODE_NOT_CONSTRAINED -> getString(R.string.template_code)
            else -> getString(R.string.template_word)
        }

        when (error) {
            GameContract.ErrorType.WORD_EMPTY, GameContract.ErrorType.CODE_EMPTY -> {
                val text = getString(R.string.game_error_word_length_zero, word)
                displayGuessError(text)
            }
            GameContract.ErrorType.WORD_LENGTH, GameContract.ErrorType.CODE_LENGTH -> {
                val text = getString(R.string.game_error_word_length, word)
                displayGuessError(text)
            }
            GameContract.ErrorType.WORD_NOT_RECOGNIZED -> {
                val text = getString(R.string.game_error_word_not_recognized, word)
                displayGuessError(text)
            }
            GameContract.ErrorType.CODE_INVALID -> {
                val text = getString(R.string.game_error_word_not_valid)
                displayGuessError(text)
            }
            GameContract.ErrorType.WORD_NOT_CONSTRAINED, GameContract.ErrorType.CODE_NOT_CONSTRAINED -> {
                val violation = violations?.firstOrNull()
                val text = when {
                    violation?.markup == Constraint.MarkupType.EXACT && violation.character != null ->
                        getString(R.string.game_error_word_constraint_exact, word, violation.character, violation.position)
                    violation?.markup == Constraint.MarkupType.INCLUDED && violation.character != null ->
                        getString(R.string.game_error_word_constraint_included, word, violation.character)
                    violation?.markup == Constraint.MarkupType.NO && violation.character != null ->
                        getString(R.string.game_error_word_constraint_no, word, violation.character)
                    violation?.markup == Constraint.MarkupType.EXACT ->
                        getString(R.string.game_error_word_constraint_exact_no_letter, word)
                    violation?.markup == Constraint.MarkupType.INCLUDED ->
                        getString(R.string.game_error_word_constraint_included_no_letter, word)
                    else -> getString(R.string.game_error_word_constraint, word)
                }
                displayGuessError(text)
            }
            GameContract.ErrorType.EVALUATION_INCONSISTENT -> {
                val text = getString(R.string.game_error_evaluation_inconsistent)
                displayEvaluationError(text)
            }
            GameContract.ErrorType.UNKNOWN -> {
                val text = getString(R.string.game_error_unknown)
                displayError(text)
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}