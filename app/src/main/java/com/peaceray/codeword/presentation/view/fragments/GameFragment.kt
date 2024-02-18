package com.peaceray.codeword.presentation.view.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.FragmentGameBinding
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.game.feedback.CharacterFeedback
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameContract
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.adapters.guess.GuessLetterAdapter
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessLetterViewHolder
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
        const val ARG_CURRENT_GUESS = "${NAME}_CURRENT_GUESS"

        fun newInstance(
            seed: String?,
            gameSetup: GameSetup,
            gameSetupUpdate: GameSetup? = null,
            currentGuess: String? = null
        ): GameFragment {
            val fragment = GameFragment()

            val args = Bundle()
            args.putString(ARG_GAME_SEED, seed)
            args.putParcelable(ARG_GAME_SETUP, gameSetup)
            args.putParcelable(ARG_GAME_SETUP_UPDATE, gameSetupUpdate ?: gameSetup)
            args.putString(ARG_CURRENT_GUESS, currentGuess)

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onGameStart(
            fragment: Fragment,
            seed: String?,
            gameSetup: GameSetup
        )

        fun onGameOver(
            fragment: GameFragment,
            seed: String?,
            gameSetup: GameSetup,
            uuid: UUID,
            solution: String?,
            rounds: Int,
            solved: Boolean,
            playerVictory: Boolean
        )
    }

    /**
     * Forfeit the game in progress. If the game is already over, this function has no effect
     * (can't forfeit a completed game).
     */
    fun forfeit() {
        presenter.onForfeit()
    }

    /**
     * Retrieve the currently-entered guess, which may be a partial string.
     */
    fun getCurrentGuess() = guessAdapter.guesses.lastOrNull()?.candidate

    /**
     * Indicate to the Fragment that it will be swapped in to replace an existing GameFragment
     * in the same state (and should therefore minimize transition animations)
     */
    fun swapIn(): GameFragment {
        swappingIn = true
        return this
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: FragmentGameBinding? = null
    private val binding get() = _binding!!
    private var keyboardView: CodeKeyboardView? = null

    @Inject lateinit var guessAdapter: GuessLetterAdapter
    lateinit var guessLayoutManager: RecyclerView.LayoutManager

    @Inject lateinit var colorSwatchManager: ColorSwatchManager

    private var swappingIn = false
    private var started = false

    var listener: OnInteractionListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = when {
            parentFragment is OnInteractionListener -> parentFragment as OnInteractionListener
            context is OnInteractionListener -> context
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


        if (swappingIn) {   // postpone entry transition until Presenter applies settings
            postponeEnterTransition()
            Timber.v("Swapping In: postponing entry transition")
        }

        // view behavior
        binding.constraintRecyclerView.adapter = guessAdapter
        binding.constraintRecyclerView.itemAnimator = GuessLetterViewHolder.ItemAnimator()
        onRecyclerViewContentHeightChange()

        // view colors
        colorSwatchManager.colorSwatchLiveData.observe(viewLifecycleOwner) { updateViewColors(it) }

        // respond to size changes
        binding.mainView.addOnLayoutChangeListener(onMainViewLayoutChangeListener)

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        // clear view status cache
        recyclerViewCellWidth = null
        recyclerViewWidth = null
        recyclerViewScrolling = null
    }

    /**
     * The Presenter is prompting the user for some action (even to "wait" for an update or a
     * Game Over display). Therefore, if the Fragment has not yet been displayed -- perhaps because
     * a transition animation was delayed -- it should at this point be shown to the screen.
     */
    private fun onPresenterPrompting() {
        if (swappingIn) {
            swappingIn = false
            // make views visible (previously invisible to shortcut animation)
            keyboardView?.visibility = View.VISIBLE
            // start entry transition
            startPostponedEnterTransition()
            Timber.v("Swapping In: starting entry transition")
        }

        if (!started) {
            started = true
            listener?.onGameStart(this, getGameSeed(), getGameSetup())
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region View Helpers
    //---------------------------------------------------------------------------------------------
    private var toast: Toast? = null
    private var recyclerViewCellWidth: Int? = null
    private var recyclerViewWidth: Int? = null
    private var recyclerViewScrolling: Boolean? = null

    private var keyboardCharacterCount: Int = 0
    private var keyboardLocale: Locale? = null
    @LayoutRes var keyboardLayout: Int = 0

    private val onMainViewLayoutChangeListener = object: View.OnLayoutChangeListener {
        override fun onLayoutChange(
            view: View?,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int
        ) {
            onMainViewContentWidthChange(right - left)
            onRecyclerViewContentHeightChange()
        }
    }

    private fun displayError(message: CharSequence) {
        toast?.cancel()
        toast = Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT)
        toast?.show()
    }

    private fun displayGuessError(message: CharSequence) {
        displayError(message)
        // rumble guess
        val length = guessAdapter.guesses.lastOrNull()?.candidate?.length ?: 0
        val rangePair = guessAdapter.activeItemRange(placeholders = length == 0)
        IntRange(rangePair.first, rangePair.first + rangePair.second - 1)
            .map { binding.constraintRecyclerView.findViewHolderForAdapterPosition(it) }
            .forEach { if (it is GuessLetterViewHolder) it.rumble() }
    }

    private fun displayEvaluationError(message: CharSequence) {
        displayError(message)
        // TODO rumble the guess?
    }

    private fun updateViewColors(swatch: ColorSwatch) {
        Timber.v("from ColorManager?: updateViewColors")
        binding.mainView.setBackgroundColor(swatch.container.background)
        binding.constraintRecyclerView.setBackgroundColor(swatch.container.background)
        binding.keyboardContainer.setBackgroundColor(swatch.container.background)
    }

    private fun onMainViewContentWidthChange(width: Int) {
        val cells = guessAdapter.itemsPerGameRow
        if (cells > 0 && width > 0 && (cells != recyclerViewCellWidth || width != recyclerViewWidth)) {
            Timber.v("cells $cells prevCells $recyclerViewCellWidth width $width prevWidth $recyclerViewWidth length ${guessAdapter.length}")
            val availableWidth = width - (binding.constraintRecyclerView.paddingStart + binding.constraintRecyclerView.paddingEnd)
            val availableWidthPerCell = availableWidth / cells
            val letterLayout = GuessLetterCellLayout.create(resources, availableWidthPerCell.toFloat())
            val pipLayout = GuessAggregateConstraintCellLayout.create(resources, guessAdapter.length, availableWidthPerCell.toFloat())

            // set the layouts
            if (guessAdapter.cellLayout[GuessLetterAdapter.ItemStyle.LETTER_MARKUP]?.layoutId != letterLayout.layoutId ||
                    guessAdapter.cellLayout[GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER]?.layoutId != pipLayout.layoutId) {
                binding.constraintRecyclerView.adapter = null
                binding.constraintRecyclerView.layoutManager = null
                guessAdapter.setCellLayouts(mapOf(
                    Pair(GuessLetterAdapter.ItemStyle.LETTER_MARKUP, letterLayout),
                    Pair(GuessLetterAdapter.ItemStyle.LETTER_CODE, letterLayout),
                    Pair(GuessLetterAdapter.ItemStyle.EMPTY, letterLayout),
                    Pair(GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER, pipLayout),
                    Pair(GuessLetterAdapter.ItemStyle.AGGREGATED_DONUT_CLUSTER, pipLayout)
                ))
                binding.constraintRecyclerView.adapter = guessAdapter
                binding.constraintRecyclerView.layoutManager = guessLayoutManager
                guessAdapter.notifyDataSetChanged()
            }

            // update for later efficiency
            recyclerViewWidth = width
            recyclerViewCellWidth = cells
        }
    }

    private fun onRecyclerViewContentHeightChange() {
        binding.constraintRecyclerView.post {
            _binding?.let {
                val height = it.constraintRecyclerView.computeVerticalScrollRange()
                val scrolling = height > it.constraintRecyclerView.height
                Timber.v("keyboard elevation check for content height $height view height ${it.constraintRecyclerView.height} scrolling $scrolling")
                if (scrolling != recyclerViewScrolling) {
                    // raise or lower keyboard
                    it.keyboardContainer.animate()
                        ?.z(if (scrolling) resources.getDimension(R.dimen.keyboard_elevation) else 0.0f)
                        ?.setDuration(resources.getInteger(android.R.integer.config_shortAnimTime).toLong())
                        ?.start()

                    recyclerViewScrolling = scrolling

                    Timber.v("keyboard elevation set for scrolling $scrolling")
                }
            }
        }
    }

    private fun onRecyclerViewActiveItemChange() {
        val positionRange = guessAdapter.activeItemRange(true)
        val position = if (positionRange.second == 0) positionRange.first else {
            positionRange.first + positionRange.second - 1
        }
        binding.constraintRecyclerView.postDelayed({
            binding.constraintRecyclerView.smoothScrollToPosition(position)
        }, 100)
    }

    private val keyboardOnKeyListener = object: CodeKeyboardView.OnKeyListener {
        override fun onCharacter(character: Char) {
            Timber.v("onCharacter $character")
            val guess = guessAdapter.guesses.lastOrNull()?.candidate ?: ""
            presenter.onGuessUpdated(guess, "$guess$character")
        }

        override fun onEnter() {
            val guess = guessAdapter.guesses.lastOrNull()?.candidate ?: ""
            presenter.onGuess(guess)
        }

        override fun onDelete() {
            val guess = guessAdapter.guesses.lastOrNull()?.candidate ?: ""
            if (guess.isNotEmpty()) {
                presenter.onGuessUpdated(guess, guess.dropLast(1))
            }
        }
    }

    private fun onKeyboardStyleUpdate(characters: Iterable<Char>, locale: Locale?) {
        val charList = characters.toList()

        if (charList.size != keyboardCharacterCount || keyboardLocale != locale) {
            val layoutId = when {
                locale != null -> R.layout.keyboard_qwerty
                charList.size <= 4 -> R.layout.keyboard_code_0_4
                charList.size <= 6 -> R.layout.keyboard_code_5_6
                charList.size <= 10 -> R.layout.keyboard_code_7_10
                charList.size <= 16 -> R.layout.keyboard_code_11_16
                else -> R.layout.keyboard_code_17_27
            }

            if (layoutId != keyboardLayout) {
                binding.keyboardContainer.removeAllViews()
                val keyboard = layoutInflater.inflate(layoutId, binding.keyboardContainer, true)
                keyboardView = keyboard.findViewById(R.id.keyboardView)
                keyboardView?.visibility = if (swappingIn) View.INVISIBLE else View.VISIBLE // invisible to shortcut animations

                keyboardView?.onKeyListener = keyboardOnKeyListener

                // TODO set keyboard background color, elevation

                keyboardLayout = layoutId
            }
        }

        // update keyboard style
        keyboardView?.keyStyle = if (locale == null) CodeKeyboardView.KeyStyle.CODE else CodeKeyboardView.KeyStyle.MARKUP
        keyboardView?.setCodeCharacters(characters)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameContract.Presenter
    private var lastMoveAt: Long = 0        // the time the last game move was entered

    override fun getGameSeed(): String? {
        return requireArguments().getString(ARG_GAME_SEED)
    }

    override fun getGameSetup(): GameSetup {
        return requireArguments().getParcelable(ARG_GAME_SETUP)!!
    }

    override fun getUpdatedGameSetup(): GameSetup {
        return requireArguments().getParcelable(ARG_GAME_SETUP_UPDATE)!!
    }

    override fun getCachedGuess(): String {
        return requireArguments().getString(ARG_CURRENT_GUESS) ?: ""
    }

    override fun setGameFieldSize(length: Int, rows: Int) {
        Timber.v("setGameFieldSize: $length $rows")
        guessAdapter.setGameFieldSize(length, rows)

        updateLayoutManager()

        onMainViewContentWidthChange(binding.mainView.width)
        onRecyclerViewContentHeightChange()
    }

    override fun setGameFieldUnlimited(length: Int) {
        Timber.v("setGameFieldUnlimited: $length")
        setGameFieldSize(length, 0)
    }

    override fun setCodeType(
        characters: Iterable<Char>,
        locale: Locale?,
        feedbackPolicy: ConstraintPolicy
    ) {
        // guessAdapter letter item styles
        val itemStyles = mutableListOf<GuessLetterAdapter.ItemStyle>()
        if (locale == null) {
            itemStyles.add(GuessLetterAdapter.ItemStyle.LETTER_CODE)
        } else {
            itemStyles.add(GuessLetterAdapter.ItemStyle.LETTER_MARKUP)
        }

        // ...and aggregated pips
        if (feedbackPolicy.isByWord()) {
            itemStyles.add(when (feedbackPolicy) {
                ConstraintPolicy.AGGREGATED_EXACT,
                ConstraintPolicy.AGGREGATED_INCLUDED -> GuessLetterAdapter.ItemStyle.AGGREGATED_DONUT_CLUSTER
                ConstraintPolicy.AGGREGATED -> GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER
                else -> throw IllegalStateException("Don't know the isByWord() policy $feedbackPolicy")
            })
        }

        // configure guessAdapter
        guessAdapter.setCodeCharacters(characters)
        guessAdapter.setItemStyles(itemStyles.toList())

        // update keyboard
        onKeyboardStyleUpdate(characters, locale)

        // update grid width if already set
        updateLayoutManager()
    }

    private fun updateLayoutManager() {
        val gridLayoutManager = GridLayoutManager(context, guessAdapter.itemsPerGameRow)
        val adapter = guessAdapter
        gridLayoutManager.spanSizeLookup = object: GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Timber.d("getSpanSize for $position: ${adapter.positionSpan(position)}")
                return adapter.positionSpan(position)
            }
        }
        guessLayoutManager = gridLayoutManager
        binding.constraintRecyclerView.layoutManager = gridLayoutManager
    }

    override fun setConstraints(constraints: List<Guess>, animate: Boolean) {
        Timber.v("setConstraints: ${constraints.size}")
        // TODO deal with [animate]
        guessAdapter.replace(constraints = constraints)

        onRecyclerViewActiveItemChange()
        onRecyclerViewContentHeightChange()
    }

    override fun setGuess(guess: Guess, animate: Boolean) {
        Timber.v("setGuess: $guess")
        // TODO deal with [animate]
        guessAdapter.advance(guess = guess)

        onRecyclerViewActiveItemChange()

        lastMoveAt = System.currentTimeMillis()
    }

    override fun replaceGuessWithConstraint(constraint: Guess, animate: Boolean) {
        Timber.v("replaceGuessWithConstraint: $constraint")
        // TODO deal with [animate]
        guessAdapter.advance(constraint = constraint)

        onRecyclerViewActiveItemChange()
        onRecyclerViewContentHeightChange()

        lastMoveAt = System.currentTimeMillis()

        if (animate) onPresenterPrompting()
    }

    override fun updateConstraint(index: Int, constraint: Guess, animate: Boolean) {
        Timber.v("updateConstraint: $index $constraint")
        // TODO deal with [animate]
        guessAdapter.update(constraints = listOf(Pair(index, constraint)))
    }

    override fun setCharacterFeedback(feedback: Map<Char, CharacterFeedback>) {
        Timber.v("setCharacterFeedback ${feedback.size}")
        keyboardView?.setCharacterFeedback(feedback)
    }

    override fun promptForGuess(suggestedGuess: Guess) {
        Timber.v("promptForGuess")
        // clear "guess" field or placeholder
        guessAdapter.advance(guess = suggestedGuess)

        keyboardView?.isEnabled = true

        onPresenterPrompting()
    }

    override fun promptForEvaluation(guess: Guess) {
        Timber.v("promptForEvaluation $guess")
        guessAdapter.advance(guess = guess)

        keyboardView?.isEnabled = false

        onPresenterPrompting()
    }

    override fun promptForWait() {
        Timber.v("promptForWait")
        keyboardView?.isEnabled = false
        // TODO disable evaluation
        // TODO show a "please wait" indicator

        onPresenterPrompting()
    }

    override fun showGameOver(
        uuid: UUID,
        solution: String?,
        rounds: Int,
        solved: Boolean,
        playerVictory: Boolean
    ) {
        Timber.v("showGameOver ($solution ${if (solved) "found" else "not found"} in $rounds) Player Victory: $playerVictory")
        keyboardView?.isEnabled = false

        // note: allow a little leeway for the last action's animation to complete before
        // showing a Game Over popup. There is no need for this delay if moves have not been
        // recently entered.
        // TODO a more formal animation scheduling system that queues game updates at a pace legible to players
        val animUnits = 0.8 + guessAdapter.itemsPerGameRow * 0.2
        val anim = resources.getInteger(android.R.integer.config_mediumAnimTime) * animUnits
        view?.postDelayed({
            listener?.onGameOver(
                this,
                getGameSeed(),
                getUpdatedGameSetup(),
                uuid,
                solution,
                rounds,
                solved,
                playerVictory
            )
        }, Math.max(0L, (lastMoveAt + anim.toLong() + 500) - System.currentTimeMillis()))

        onPresenterPrompting()
    }

    override fun showError(error: GameContract.ErrorType, violations: List<Constraint.Violation>?) {
        onPresenterPrompting()

        val isWord = getGameSetup().vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST
        val word = if (isWord) {
            getString(R.string.template_word)
        } else {
            getString(R.string.template_code)
        }

        when (error) {
            GameContract.ErrorType.GUESS_EMPTY -> {
                val text = getString(R.string.game_error_word_length_zero, word)
                displayGuessError(text)
            }
            GameContract.ErrorType.GUESS_LENGTH -> {
                val text = getString(R.string.game_error_word_length, word)
                displayGuessError(text)
            }
            GameContract.ErrorType.GUESS_NOT_CONSTRAINED -> {
                val violation = violations?.firstOrNull()
                val text = when {
                    violation?.markup == Constraint.MarkupType.EXACT && violation.character != null ->
                        getString(R.string.game_error_word_constraint_exact, word, violation.character, violation.position!! + 1)
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
            GameContract.ErrorType.GUESS_LETTER_REPETITIONS -> {
                val violation = violations?.firstOrNull()
                val text = if (violation?.character != null) {
                    getString(R.string.game_error_word_letter_repetitions, violation.character)
                } else {
                    getString(R.string.game_error_word_letter_repetitions_no_letter)
                }
                displayGuessError(text)
            }
            GameContract.ErrorType.GUESS_INVALID -> {
                val text = if (isWord) getString(R.string.game_error_word_not_recognized, word) else {
                    getString(R.string.game_error_word_not_valid)
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