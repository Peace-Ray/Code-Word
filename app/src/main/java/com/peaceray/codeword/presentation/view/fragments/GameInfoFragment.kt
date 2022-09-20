package com.peaceray.codeword.presentation.view.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.GameInfoBinding
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.datamodel.Guess
import com.peaceray.codeword.presentation.datamodel.GuessLetter
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.adapters.guess.GuessLetterAdapter
import com.peaceray.codeword.presentation.view.component.layouts.CellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout
import com.peaceray.codeword.presentation.view.component.viewholders.ConstraintPipGridViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessLetterViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterCodeAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessLetterMarkupAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewListenerAdapter
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewPuzzleTypeViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewSeedViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewViewHolder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class GameInfoFragment: Fragment(R.layout.game_info), GameSetupContract.View {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    enum class Sections {
        HOW_TO_PLAY,
        SEED,
        PUZZLE_INFO,
        CREATE_PUZZLE,

        DIFFICULTY;
    }

    companion object {

        const val NAME = "GameInfoFragment"
        const val ARG_GAME_SEED = "${NAME}_GAME_SEED"
        const val ARG_GAME_SETUP = "${NAME}_GAME_SETUP"
        const val ARG_SECTIONS = "${NAME}_SECTIONS"

        fun newInstance(seed: String?, setup: GameSetup, sections: Array<Sections> = Sections.values()): GameInfoFragment {
            val fragment = GameInfoFragment()
            fragment.arguments = newArguments(seed, setup)
            return fragment
        }

        fun newArguments(seed: String?, setup: GameSetup, sections: Array<Sections> = Sections.values()): Bundle {
            val args = Bundle()
            args.putString(ARG_GAME_SEED, seed)
            args.putParcelable(ARG_GAME_SETUP, setup)
            args.putIntArray(ARG_SECTIONS, sections.map { it.ordinal }.toIntArray())
            return args
        }
    }

    interface OnInteractionListener {
        fun onInfoCreatePuzzleClicked(fragment: GameInfoFragment)
        fun onInfoFinished(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup)
        fun onInfoChanged(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup)
        fun onInfoCanceled(fragment: GameInfoFragment)
    }

    /**
     * Respond to a button press on a "launch" button which exists outside of this Fragment.
     */
    fun onLaunchButtonClicked() {
        presenter.onLaunchButtonClicked()
    }

    /**
     * Respond to a button press on a "cancel" button which exists outside of this Fragment.
     */
    fun onCancelButtonClicked() {
        presenter.onCancelButtonClicked()
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: GameInfoBinding? = null
    private val binding get() = _binding!!

    // ViewHolder section wrappers
    lateinit var seedViewHolder: GameReviewSeedViewHolder
    lateinit var puzzleTypeViewHolder: GameReviewPuzzleTypeViewHolder

    // Legend recycler controls
    @Inject lateinit var legendAdapter: GuessLetterAdapter
    lateinit var legendLayoutManager: RecyclerView.LayoutManager

    // Legend Data
    private lateinit var legendGuess: String
    private lateinit var legendSecret: String

    // Legend View Holders
    private val legendLetterViewHolders = mutableListOf<GuessLetterViewHolder>()
    private val legendPipViewHolders = mutableListOf<ConstraintPipGridViewHolder>()
    private var legendCodeCharacters = listOf<Char>()

    // manager / inflater
    @Inject lateinit var inflater: LayoutInflater
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var colorSwatchManager: ColorSwatchManager

    var listener: OnInteractionListener? = null
    val sections by lazy {
        requireArguments().getIntArray(ARG_SECTIONS)?.map { Sections.values()[it] }
            ?: Sections.values().toList()
    }

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
        _binding = GameInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // section visibility
        for (section in Sections.values()) {
            when(section) {
                Sections.HOW_TO_PLAY -> binding.sectionHowToPlay.mainViewHowToPlay
                Sections.SEED -> binding.sectionSeed.mainViewSeed
                Sections.PUZZLE_INFO -> binding.sectionPuzzleType.mainViewPuzzleType
                Sections.CREATE_PUZZLE -> binding.sectionCreatePuzzle.mainViewCreatePuzzle
                Sections.DIFFICULTY -> binding.sectionDifficulty.mainViewDifficulty
            }.visibility = if (section in sections) View.VISIBLE else View.GONE
        }

        // create puzzle button
        binding.sectionCreatePuzzle.createPuzzleButton.setOnClickListener(createPuzzleListener)

        // apply difficulty settings
        binding.sectionDifficulty.hardModeCheckBox.setOnCheckedChangeListener(hardModeListener)
        binding.sectionDifficulty.limitedRoundsCheckBox.setOnCheckedChangeListener(roundLimitedListener)
        binding.sectionDifficulty.roundsSeekBar.setOnSeekBarChangeListener(roundsListener)

        // view colors
        colorSwatchManager.colorSwatchLiveData.observe(viewLifecycleOwner) { updateViewColors(it) }

        // create section view holders
        seedViewHolder = GameReviewSeedViewHolder(binding.sectionSeed.mainViewSeed, colorSwatchManager, gameSetupListener)
        puzzleTypeViewHolder = GameReviewPuzzleTypeViewHolder(binding.sectionPuzzleType.mainViewPuzzleType, colorSwatchManager, gameSetupListener)

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configureHowToPlay(gameSetup: GameSetup) {
        Timber.v("creating legend with code characters $legendCodeCharacters")

        // setup legend recycler view with blanks
        binding.sectionHowToPlay.recyclerView.adapter = legendAdapter
        binding.sectionHowToPlay.recyclerView.itemAnimator = GuessLetterViewHolder.ItemAnimator()

        legendLetterViewHolders.clear()
        legendPipViewHolders.clear()

        if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.LIST) {
            // medium legend size
            val letterLayout = GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.MEDIUM)
            val pipLayout = GuessAggregateConstraintCellLayout.create(resources, CellLayout.SizeCategory.MEDIUM)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_CODE, letterLayout)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_MARKUP, letterLayout)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER, pipLayout)

            legendAdapter.setCodeCharacters(legendCodeCharacters)
            legendAdapter.setItemStyles(GuessLetterAdapter.ItemStyle.LETTER_MARKUP)

            legendGuess = getString(R.string.game_info_legend_guess)
            legendSecret = getString(R.string.game_info_legend_secret)

            // show potentially hidden
            binding.sectionHowToPlay.legendNoLetter1.visibility = View.VISIBLE
            binding.sectionHowToPlay.legendNoLetter2.visibility = View.VISIBLE
            binding.sectionHowToPlay.legendNoText.visibility = View.VISIBLE

            // hide unused
            binding.sectionHowToPlay.legendPresentPips.visibility = View.GONE
            binding.sectionHowToPlay.legendCorrectPips.visibility = View.GONE
            binding.sectionHowToPlay.legendPresentLetter2.visibility = View.GONE

            // setup potentially altered text
            binding.sectionHowToPlay.legendCorrectText.text = getString(R.string.game_info_legend_correct)
            binding.sectionHowToPlay.legendPresentText.text = getString(R.string.game_info_legend_present)

            // setup legend view holders for letters
            val appearance = GuessLetterMarkupAppearance(requireContext(), letterLayout)
            createLetterViewHolder(binding.sectionHowToPlay.legendCorrectLetter.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_correct_letter)[0], Constraint.MarkupType.EXACT))
            createLetterViewHolder(binding.sectionHowToPlay.legendPresentLetter1.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_present_letter)[0], Constraint.MarkupType.INCLUDED))
            createLetterViewHolder(binding.sectionHowToPlay.legendNoLetter1.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_no_letter_1)[0], Constraint.MarkupType.NO))
            createLetterViewHolder(binding.sectionHowToPlay.legendNoLetter2.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_no_letter_2)[0], Constraint.MarkupType.NO))
        } else {
            // small legend size
            val letterLayout = GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.SMALL)
            val pipLayout = GuessAggregateConstraintCellLayout.create(resources, CellLayout.SizeCategory.SMALL)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_CODE, letterLayout)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_MARKUP, letterLayout)
            legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER, pipLayout)

            legendAdapter.setCodeCharacters(legendCodeCharacters)
            legendAdapter.setItemStyles(GuessLetterAdapter.ItemStyle.LETTER_CODE, GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER)

            // setup legend recycler view with blanks
            binding.sectionHowToPlay.recyclerView.adapter = legendAdapter
            binding.sectionHowToPlay.recyclerView.itemAnimator = GuessLetterViewHolder.ItemAnimator()

            legendGuess = getString(R.string.game_info_legend_code_guess)
            legendSecret = getString(R.string.game_info_legend_code_secret)

            // show potentially hidden
            binding.sectionHowToPlay.legendPresentLetter2.visibility = View.VISIBLE
            binding.sectionHowToPlay.legendPresentPips.visibility = View.VISIBLE
            binding.sectionHowToPlay.legendCorrectPips.visibility = View.VISIBLE

            // hide unused
            binding.sectionHowToPlay.legendNoLetter1.visibility = View.GONE
            binding.sectionHowToPlay.legendNoLetter2.visibility = View.GONE
            binding.sectionHowToPlay.legendNoText.visibility = View.GONE

            // setup potentially altered text
            binding.sectionHowToPlay.legendCorrectText.text = getString(R.string.game_info_legend_code_correct)
            binding.sectionHowToPlay.legendPresentText.text = getString(R.string.game_info_legend_code_present)

            // setup legend view holders for letters
            val appearance = GuessLetterCodeAppearance(requireContext(), letterLayout, legendCodeCharacters)
            createLetterViewHolder(binding.sectionHowToPlay.legendCorrectLetter.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_code_correct_letter)[0], Constraint.MarkupType.EXACT))
            createLetterViewHolder(binding.sectionHowToPlay.legendPresentLetter1.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_code_present_letter_1)[0], Constraint.MarkupType.INCLUDED))
            createLetterViewHolder(binding.sectionHowToPlay.legendPresentLetter2.getChildAt(0), appearance)
                .bind(GuessLetter(getString(R.string.game_info_legend_code_present_letter_2)[0], Constraint.MarkupType.INCLUDED))

            // setup legend view holders for pips
            createPipsViewHolder(binding.sectionHowToPlay.legendCorrectPips.getChildAt(0))
                .bind(createGuess(4, 1, 0))
            createPipsViewHolder(binding.sectionHowToPlay.legendPresentPips.getChildAt(0))
                .bind(createGuess(4, 0, 2))
        }

        legendAdapter.setGameFieldSize(legendGuess.length, 2)
        legendAdapter.setCodeCharacters(legendCodeCharacters)
        legendLayoutManager = GridLayoutManager(context, legendAdapter.itemsPerGameRow)
        binding.sectionHowToPlay.recyclerView.layoutManager = legendLayoutManager

        // TODO apply animation to these
        legendAdapter.replace(constraints = listOf(
            Constraint.Companion.create(legendGuess, legendSecret),
            Constraint.Companion.create(legendSecret, legendSecret)
        ))
    }

    private fun createLetterViewHolder(itemView: View, appearance: GuessLetterAppearance): GuessLetterViewHolder {
        val vh = GuessLetterViewHolder(itemView, colorSwatchManager, appearance)
        legendLetterViewHolders.add(vh)
        return vh
    }

    private fun updateLetterViewHolders(codeLetters: Iterable<Char>, locale: Locale?) {
        val itemViewBinding = legendLetterViewHolders.map { Pair(it.itemView, it.guess) }
        legendLetterViewHolders.clear()

        val cellLayout = if (gameSetup?.vocabulary?.type == GameSetup.Vocabulary.VocabularyType.LIST) {
            GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.MEDIUM)
        } else {
            GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.SMALL)
        }

        legendCodeCharacters = codeLetters.distinct().sorted()
        Timber.v("updating view holders with legendCodeCharacters $legendCodeCharacters for ${itemViewBinding.size} views")
        val appearance = if (locale != null) {
            GuessLetterMarkupAppearance(requireContext(), cellLayout)
        } else {
            GuessLetterCodeAppearance(requireContext(), cellLayout, legendCodeCharacters)
        }
        legendLetterViewHolders.addAll(itemViewBinding.map {
            val vh = createLetterViewHolder(it.first, appearance)
            vh.bind(it.second)
            vh
        })

        binding.sectionHowToPlay.recyclerView.adapter = null
        legendAdapter.setCodeCharacters(legendCodeCharacters)
        binding.sectionHowToPlay.recyclerView.adapter = legendAdapter
    }

    private fun createPipsViewHolder(itemView: View): ConstraintPipGridViewHolder {
        val vh = ConstraintPipGridViewHolder(itemView, colorSwatchManager)
        legendPipViewHolders.add(vh)
        return vh
    }

    private fun createGuess(length: Int, exact: Int, included: Int): Guess {
        val guess = List(length) { "A" }.joinToString("")
        val markup = List(length) { when {
            it < exact -> Constraint.MarkupType.EXACT
            it < exact + included -> Constraint.MarkupType.INCLUDED
            else -> Constraint.MarkupType.NO
        } }

        return Guess(Constraint.create(guess, markup))
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Listeners, Adapters, View Helpers
    //---------------------------------------------------------------------------------------------
    private var roundsAllowed: List<Int> = listOf()
    private var unlimitedRoundsAllowed = false

    private val puzzleTypeMutableFeatures = mutableSetOf<GameSetupContract.Feature>()

    private val gameSetupListener = object: GameReviewListenerAdapter() {
        override fun onCopySeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
            // copy to clipboard
            if (seed != null) {
                val clip = ClipData.newPlainText(getString(R.string.clip_seed_label), seed)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(context, getString(R.string.clip_seed_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val createPuzzleListener = object: View.OnClickListener {
        override fun onClick(p0: View?) {
            // pass to container; irrelevant to the Contract Presenter
            listener?.onInfoCreatePuzzleClicked(this@GameInfoFragment)
        }
    }

    private val seedClickListener = object: View.OnClickListener {
        override fun onClick(p0: View?) {
            // copy to clipboard
            if (seed != null) {
                val clip = ClipData.newPlainText(getString(R.string.clip_seed_label), seed!!)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(context, getString(R.string.clip_seed_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val roundsListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            if (roundsAllowed.size > progress) {
                val chars = roundsAllowed[progress]
                binding.sectionDifficulty.roundsPrompt.text =
                    getString(R.string.game_setup_rounds_prompt, chars)
            }
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            // no effect
        }

        override fun onStopTrackingTouch(seekbar: SeekBar?) {
            if (seekbar != null && roundsAllowed.size > seekbar.progress) {
                presenter.onFeatureEntered(
                    GameSetupContract.Feature.ROUNDS,
                    roundsAllowed[seekbar.progress]
                )
            }
        }
    }

    private val hardModeListener = object: CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(button: CompoundButton?, checked: Boolean) {
            if (!presenter.onFeatureEntered(GameSetupContract.Feature.HARD_MODE, checked)) {
                button?.isChecked = !checked
            }
        }
    }

    private val roundLimitedListener = object: CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(button: CompoundButton?, checked: Boolean) {
            if (checked) {
                binding.sectionDifficulty.roundsContainer.visibility = View.VISIBLE

                val rounds = roundsAllowed[binding.sectionDifficulty.roundsSeekBar.progress]
                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, rounds)
            } else {
                binding.sectionDifficulty.roundsContainer.visibility = View.GONE

                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, 0)
            }
        }
    }

    private fun setFeatureVisibility(feature: GameSetupContract.Feature, visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        when (feature) {
            GameSetupContract.Feature.SEED -> {
                if (Sections.SEED in sections) binding.sectionSeed.mainViewSeed.visibility = visibility
            }
            GameSetupContract.Feature.HARD_MODE -> binding.sectionDifficulty.hardModeCheckBox.visibility = visibility
            GameSetupContract.Feature.ROUNDS -> binding.sectionDifficulty.roundsContainer.visibility = visibility
            GameSetupContract.Feature.LAUNCH -> {
                // TODO communicate this to Listener? Set button enabled?
            }
        }
    }

    private fun setFeatureMutability(feature: GameSetupContract.Feature, mutable: Boolean) {
        Timber.v("setFeatureMutability $feature is mutable $mutable")
        when (feature) {
            GameSetupContract.Feature.SEED -> seedViewHolder.bind(mutable = mutable)
            GameSetupContract.Feature.PLAYER_ROLE,
            GameSetupContract.Feature.CODE_LANGUAGE,
            GameSetupContract.Feature.CODE_LENGTH,
            GameSetupContract.Feature.CODE_CHARACTERS,
            GameSetupContract.Feature.EVALUATOR_HONEST -> {
                val mutableBefore = puzzleTypeViewHolder.mutable
                if (mutable) puzzleTypeMutableFeatures.add(feature) else puzzleTypeMutableFeatures.remove(feature)
                val mutableAfter = puzzleTypeMutableFeatures.isNotEmpty()
                if (mutableBefore != mutableAfter) puzzleTypeViewHolder.bind(mutable = mutableAfter)
            }
            GameSetupContract.Feature.HARD_MODE -> setMutability(binding.sectionDifficulty.hardModeCheckBox, mutable)
            GameSetupContract.Feature.ROUNDS -> {
                setMutability(binding.sectionDifficulty.limitedRoundsCheckBox, mutable)
                setMutability(binding.sectionDifficulty.roundsSeekBar, mutable)
            }
            GameSetupContract.Feature.LAUNCH -> {
                // TODO: communicate this with listener? Set button enabled?
            }
        }
    }

    private fun setMutability(control: SeekBar, mutable: Boolean) {
        control.isEnabled = mutable
    }

    private fun setMutability(control: CheckBox, mutable: Boolean) {
        control.isClickable = mutable
        control.isEnabled = mutable
    }

    private fun setFeatureProgress(feature: GameSetupContract.Feature, value: Int) {
        val seekbar: SeekBar
        val label: TextView
        val labelText: String
        val values: List<Int>
        when(feature) {
            GameSetupContract.Feature.ROUNDS -> {
                seekbar = binding.sectionDifficulty.roundsSeekBar
                label = binding.sectionDifficulty.roundsPrompt
                labelText = getString(R.string.game_setup_rounds_prompt, value)
                values = roundsAllowed
            }
            else -> {
                Timber.w("Can't set feature progress for feature $feature")
                return
            }
        }

        val index = values.indexOf(value)
        if (index >= 0) {
            seekbar.isEnabled = true
            seekbar.progress = index
            label.text = labelText
        } else {
            seekbar.isEnabled = false
            label.text = labelText
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region View helpers
    //---------------------------------------------------------------------------------------------
    private fun updateViewColors(swatch: ColorSwatch) {
        binding.mainView.setBackgroundColor(swatch.container.background)
        binding.sectionHowToPlay.recyclerView.setBackgroundColor(swatch.container.background)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameSetupContract.Presenter

    private var seed: String? = null
    private var gameSetup: GameSetup? = null
    private var gameProgress: GameStatusReview.Status? = null

    override fun getType(): GameSetupContract.Type = GameSetupContract.Type.ONGOING

    override fun getQualifiers() = emptySet<GameSetupContract.Qualifier>()

    override fun getOngoingGameSetup(): Pair<String?, GameSetup> {
        val bundle = requireArguments()
        val seed = bundle.getString(ARG_GAME_SEED)
        val setup = bundle.getParcelable<GameSetup>(ARG_GAME_SETUP)
        return Pair(seed, setup!!)
    }

    override fun finishGameSetup(review: GameStatusReview) {
        val seed = review.seed
        val setup = review.setup
        Timber.v("finishGameSetup with seed $seed setup $setup")
        listener?.onInfoFinished(this, seed, setup)
    }

    override fun cancelGameSetup() {
        Timber.v("cancelGameSetup")
        listener?.onInfoCanceled(this)
    }

    override fun setGameStatusReview(review: GameStatusReview) {
        val seed = review.seed
        val gameSetup = review.setup
        val progress = review.status

        val changed = (seed != this.seed || gameSetup != this.gameSetup)

        this.seed = seed
        this.gameSetup = gameSetup
        this.gameProgress = progress

        // configure How To Play (only if visible)
        if (Sections.HOW_TO_PLAY in sections) {
            configureHowToPlay(gameSetup)
        }

        val templateCode = getString(when(gameSetup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> R.string.template_word
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> R.string.template_code
        })
        val templateLanguageCode = getString(when(gameSetup.vocabulary.language){
            CodeLanguage.ENGLISH -> R.string.template_english_secret
            CodeLanguage.CODE -> R.string.template_code_secret
        })

        seedViewHolder.bind(review = review)
        puzzleTypeViewHolder.bind(review = review)

        // set values top to bottom
        binding.sectionHowToPlay.gameInfoExplanation.text = if (gameSetup.board.rounds in 1..100) {
            getString(R.string.game_info_explanation, templateLanguageCode, templateCode, gameSetup.vocabulary.length, gameSetup.board.rounds)
        } else {
            getString(R.string.game_info_explanation_unlimited, templateLanguageCode, templateCode, gameSetup.vocabulary.length)
        }

        setFeatureVisibility(GameSetupContract.Feature.SEED, seed != null)
        setFeatureProgress(GameSetupContract.Feature.ROUNDS, gameSetup.board.rounds)

        binding.sectionDifficulty.hardModeCheckBox.isChecked = gameSetup.evaluation.enforced != ConstraintPolicy.IGNORE
        binding.sectionDifficulty.limitedRoundsCheckBox.isChecked = gameSetup.board.rounds > 0
        binding.sectionDifficulty.roundsContainer.visibility = if (gameSetup.board.rounds > 0) View.VISIBLE else View.GONE

        if (changed) listener?.onInfoChanged(this, seed, gameSetup)
    }

    override fun showError(feature: GameSetupContract.Feature, error: GameSetupContract.Error, qualifier: GameSetupContract.Qualifier?) {
        Timber.e("Would show $feature error $error w/ qualifier $qualifier but don't know how")
    }

    override fun setCodeLanguage(characters: Iterable<Char>, locale: Locale) {
        updateLetterViewHolders(characters, locale)
    }

    override fun setCodeComposition(characters: Iterable<Char>) {
        updateLetterViewHolders(characters, null)
    }

    override fun setFeatureAvailability(
        availabilities: Map<GameSetupContract.Feature, GameSetupContract.Availability>,
        qualifiers: Map<GameSetupContract.Feature, GameSetupContract.Qualifier>,
        defaultAvailability: GameSetupContract.Availability
    ) {
        // iterate through all features, not just those provided
        for (feature in GameSetupContract.Feature.values()) {
            setFeatureAvailability(feature, availabilities[feature] ?: defaultAvailability, qualifiers[feature])
        }
    }

    override fun setFeatureAvailability(feature: GameSetupContract.Feature, availability: GameSetupContract.Availability, qualifier: GameSetupContract.Qualifier?) {
        setFeatureMutability(feature, availability == GameSetupContract.Availability.AVAILABLE)
        setFeatureVisibility(feature, availability != GameSetupContract.Availability.DISABLED)

        when (qualifier) {
            GameSetupContract.Qualifier.VERSION_CHECK_PENDING,
            GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE,
            GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED,
            GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED -> {
                // TODO note or display Qualifier for this Feature
            }
            null -> {
                // TODO clear Qualifier for this feature
            }
        }
    }

    override fun setFeatureValuesAllowed(feature: GameSetupContract.Feature, values: List<Int>) {
        val value = when (feature) {
            GameSetupContract.Feature.ROUNDS -> {
                unlimitedRoundsAllowed = 0 in values
                roundsAllowed = if (!unlimitedRoundsAllowed) values else {
                    val rounds = values.toMutableList()
                    rounds.remove(0)
                    rounds.toList()
                }
                binding.sectionDifficulty.roundsSeekBar.max = roundsAllowed.size - 1
                val allowUnlimitedToggle = unlimitedRoundsAllowed && roundsAllowed.isNotEmpty()
                binding.sectionDifficulty.limitedRoundsCheckBox.isEnabled = allowUnlimitedToggle
                binding.sectionDifficulty.limitedRoundsCheckBox.isClickable = allowUnlimitedToggle
                gameSetup?.board?.rounds
            }
            else -> {
                Timber.w("Unsupported feature w/ Int values $feature")
                null
            }
        }

        // update feature progress now that limits have changed (this might make the feature
        // enabled / disabled)
        if (value != null) {
            setFeatureProgress(feature, value)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}