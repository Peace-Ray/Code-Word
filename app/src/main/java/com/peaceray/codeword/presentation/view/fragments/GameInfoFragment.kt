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
import com.peaceray.codeword.presentation.datamodel.guess.Guess
import com.peaceray.codeword.presentation.datamodel.guess.GuessLetter
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.adapters.guess.GuessLetterAdapter
import com.peaceray.codeword.presentation.view.component.layouts.CellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout
import com.peaceray.codeword.presentation.view.component.layouts.GuessLetterCellLayout
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessAggregatedPipGridViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.GuessLetterViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedCountsPipAppearance
import com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance.GuessAggregatedCountsDonutAppearance
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
import kotlin.math.min

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
    private lateinit var legendConstraint: Constraint

    // Legend View Holders
    private val legendLetterViewHolders = mutableListOf<GuessLetterViewHolder>()
    private val legendPipViewHolders = mutableListOf<GuessAggregatedPipGridViewHolder>()
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

        // configuration based on language
        when (gameSetup.vocabulary.type) {
            GameSetup.Vocabulary.VocabularyType.LIST -> {
                // vocabulary list: language words
                legendGuess = getString(R.string.game_info_legend_guess)
                legendSecret = getString(R.string.game_info_legend_secret)
            }
            GameSetup.Vocabulary.VocabularyType.ENUMERATED -> {
                // enumerated codes: ABCD
                legendGuess = getString(R.string.game_info_legend_code_guess)
                legendSecret = getString(R.string.game_info_legend_code_secret)
            }
        }

        // configuration based on evaluation type
        val letterLayout: GuessLetterCellLayout
        val pipLayout: GuessAggregateConstraintCellLayout
        val showPips: Boolean
        val letterAppearance: GuessLetterAppearance
        val pipAppearance: GuessAggregatedAppearance
        when (gameSetup.evaluation.type) {
            ConstraintPolicy.AGGREGATED_EXACT,
            ConstraintPolicy.AGGREGATED_INCLUDED,
            ConstraintPolicy.AGGREGATED -> {
                // small legend size
                letterLayout = GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.SMALL)
                pipLayout = GuessAggregateConstraintCellLayout.create(resources, legendGuess.length, CellLayout.SizeCategory.SMALL)
                showPips = true

                // item styles
                val letterStyle = when (gameSetup.vocabulary.type) {
                    GameSetup.Vocabulary.VocabularyType.LIST -> GuessLetterAdapter.ItemStyle.LETTER_MARKUP
                    GameSetup.Vocabulary.VocabularyType.ENUMERATED -> GuessLetterAdapter.ItemStyle.LETTER_CODE
                }
                val aggregatedStyle = when (gameSetup.evaluation.type) {
                    ConstraintPolicy.AGGREGATED_EXACT,
                    ConstraintPolicy.AGGREGATED_INCLUDED -> GuessLetterAdapter.ItemStyle.AGGREGATED_DONUT_CLUSTER
                    ConstraintPolicy.AGGREGATED -> GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER
                    else -> throw IllegalStateException("Evaluation type ${gameSetup.evaluation.type} changed?")
                }
                legendAdapter.setItemStyles(letterStyle, aggregatedStyle)

                // appearances
                letterAppearance = if (gameSetup.vocabulary.type == GameSetup.Vocabulary.VocabularyType.ENUMERATED) {
                    GuessLetterCodeAppearance(requireContext(), letterLayout, legendCodeCharacters)
                } else {
                    GuessLetterMarkupAppearance(requireContext(), letterLayout)
                }
                pipAppearance = when (gameSetup.evaluation.type) {
                    ConstraintPolicy.AGGREGATED_EXACT,
                    ConstraintPolicy.AGGREGATED_INCLUDED -> GuessAggregatedCountsDonutAppearance(requireContext(), pipLayout)
                    ConstraintPolicy.AGGREGATED -> GuessAggregatedCountsPipAppearance(requireContext(), pipLayout)
                    else -> throw IllegalArgumentException("Can't ever happen; unsupported evaluation ${gameSetup.evaluation.type}")
                }
            }
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> {
                // medium legend size
                letterLayout = GuessLetterCellLayout.create(resources, CellLayout.SizeCategory.MEDIUM)
                pipLayout = GuessAggregateConstraintCellLayout.create(resources, legendGuess.length, CellLayout.SizeCategory.MEDIUM)
                showPips = false

                // item styles
                legendAdapter.setItemStyles(GuessLetterAdapter.ItemStyle.LETTER_MARKUP)

                // appearances
                letterAppearance = GuessLetterMarkupAppearance(requireContext(), letterLayout)
                pipAppearance = GuessAggregatedCountsPipAppearance(requireContext(), pipLayout)
            }
            else -> throw IllegalArgumentException("ConstraintPolicy ${gameSetup.evaluation.type} is not supported")
        }

        // legend constraint and letters
        legendConstraint = Constraint.create(legendGuess, legendSecret)
        val markupLetters = getLegendMarkupLetters(gameSetup, legendConstraint)
        val markupLabels = getLegendMarkupLabels(gameSetup, markupLetters)
        val markupViews = getLegendMarkupViews()

        // populate and label
        for (markup in listOf(Constraint.MarkupType.EXACT, Constraint.MarkupType.INCLUDED, Constraint.MarkupType.NO)) {
            val letters = markupLetters[markup]
            val label = markupLabels[markup]
            val views = markupViews[markup]

            if (letters.isNullOrEmpty() || label == null || views == null) {
                views?.setVisibility(View.GONE)
            } else {
                views.setVisibility(View.VISIBLE)
                views.textView.text = label
                views.letterViews.forEachIndexed { index, view ->
                    if (index < letters.size) {
                        createLetterViewHolder(view.getChildAt(0), letterAppearance)
                            .bind(GuessLetter(0, letters[index], markup))
                    } else {
                        view.visibility = View.GONE
                    }
                }
                if (showPips && views.pipView != null && letters.isNotEmpty()) {
                    val guess = when (markup) {
                        Constraint.MarkupType.EXACT -> createGuess(legendConstraint.candidate.length, letters.size, 0)
                        Constraint.MarkupType.INCLUDED -> createGuess(legendConstraint.candidate.length, 0, letters.size)
                        Constraint.MarkupType.NO -> createGuess(legendConstraint.candidate.length, 0, 0)
                    }
                    createPipsViewHolder(views.pipView.getChildAt(0), pipAppearance).bind(guess.evaluation)
                } else {
                    views.pipView?.visibility = View.GONE
                }
            }
        }

        legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_CODE, letterLayout)
        legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.LETTER_MARKUP, letterLayout)
        legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.AGGREGATED_PIP_CLUSTER, pipLayout)
        legendAdapter.setCellLayout(GuessLetterAdapter.ItemStyle.AGGREGATED_DONUT_CLUSTER, pipLayout)

        legendAdapter.setCodeCharacters(legendCodeCharacters)


        legendAdapter.setGameFieldSize(legendGuess.length, 2)
        legendAdapter.setCodeCharacters(legendCodeCharacters)
        legendLayoutManager = GridLayoutManager(context, legendAdapter.itemsPerGameRow)
        binding.sectionHowToPlay.recyclerView.layoutManager = legendLayoutManager

        // TODO apply animation to these
        legendAdapter.replace(constraints = listOf(
            Guess.createPerfectEvaluation(legendConstraint),
            Guess.createPerfectEvaluation(Constraint.Companion.create(legendSecret, legendSecret))
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

    private fun createPipsViewHolder(itemView: View, appearance: GuessAggregatedAppearance): GuessAggregatedPipGridViewHolder {
        val vh = GuessAggregatedPipGridViewHolder(itemView, colorSwatchManager, appearance)
        legendPipViewHolders.add(vh)
        return vh
    }

    private fun getLegendMarkupLetters(gameSetup: GameSetup, constraint: Constraint): Map<Constraint.MarkupType, List<Char>> {
        val zipped = constraint.candidate.toList().zip(constraint.markup)
        var exactLetters: List<Char> = emptyList()
        var includedLetters: List<Char> = emptyList()
        var noLetters: List<Char>
        when (gameSetup.evaluation.type) {
            ConstraintPolicy.AGGREGATED_EXACT -> {
                exactLetters = zipped.filter { it.second == Constraint.MarkupType.EXACT }.map { it.first }
                noLetters = zipped.filter { it.second != Constraint.MarkupType.EXACT }.map { it.first }
            }
            ConstraintPolicy.AGGREGATED_INCLUDED -> {
                includedLetters = zipped.filter {
                    it.second in setOf(Constraint.MarkupType.INCLUDED, Constraint.MarkupType.EXACT)
                }.map { it.first }
                noLetters = zipped.filter { it.second == Constraint.MarkupType.NO }.map { it.first }
            }
            ConstraintPolicy.AGGREGATED -> {
                exactLetters = zipped.filter { it.second == Constraint.MarkupType.EXACT }.map { it.first }
                includedLetters = zipped.filter { it.second == Constraint.MarkupType.INCLUDED }.map { it.first }
                noLetters = zipped.filter { it.second == Constraint.MarkupType.NO }.map { it.first }
            }
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> {
                exactLetters = zipped.filter { it.second == Constraint.MarkupType.EXACT }.map { it.first }
                includedLetters = zipped.filter { it.second == Constraint.MarkupType.INCLUDED }.map { it.first }
                noLetters = zipped.filter { it.second == Constraint.MarkupType.NO }.map { it.first }
            }
            else -> throw IllegalArgumentException("No support for ${gameSetup.evaluation.type}")
        }

        return mapOf(
            Pair(Constraint.MarkupType.EXACT, exactLetters),
            Pair(Constraint.MarkupType.INCLUDED, includedLetters),
            Pair(Constraint.MarkupType.NO, noLetters)
        )
    }

    private fun getLegendMarkupLabels(gameSetup: GameSetup, letters: Map<Constraint.MarkupType, List<Char>>): Map<Constraint.MarkupType, String> {
        val map = mutableMapOf<Constraint.MarkupType, String>()
        val pick: (markup: Constraint.MarkupType, arrayResId: Int) -> Unit = { markup, arrayResId ->
            val array = requireContext().resources.getStringArray(arrayResId)
            val index = min(array.size - 1, letters[markup]!!.size - 1)
            map[markup] = array[index]
        }

        when (gameSetup.evaluation.type) {
            ConstraintPolicy.AGGREGATED_EXACT -> {
                pick(Constraint.MarkupType.EXACT, R.array.game_info_legend_correct_count)
                pick(Constraint.MarkupType.NO, R.array.game_info_legend_not_correct_count)
            }
            ConstraintPolicy.AGGREGATED_INCLUDED -> {
                pick(Constraint.MarkupType.INCLUDED, R.array.game_info_legend_included_or_exact_count)
                pick(Constraint.MarkupType.NO, R.array.game_info_legend_no_count)
            }
            ConstraintPolicy.AGGREGATED -> {
                pick(Constraint.MarkupType.EXACT, R.array.game_info_legend_correct_count)
                pick(Constraint.MarkupType.INCLUDED, R.array.game_info_legend_included_count)
                pick(Constraint.MarkupType.NO, R.array.game_info_legend_no_count)
            }
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> {
                pick(Constraint.MarkupType.EXACT, R.array.game_info_legend_correct_shown)
                pick(Constraint.MarkupType.INCLUDED, R.array.game_info_legend_included_shown)
                pick(Constraint.MarkupType.NO, R.array.game_info_legend_no_shown)
            }
            else -> throw IllegalArgumentException("No support for ${gameSetup.evaluation.type}")
        }

        return map.toMap()
    }

    private fun getLegendMarkupViews(): Map<Constraint.MarkupType, LegendMarkupViews> {
        return mapOf(
            Pair(Constraint.MarkupType.EXACT, LegendMarkupViews(
                binding.sectionHowToPlay.legendCorrectText,
                listOf(
                    binding.sectionHowToPlay.legendCorrectLetter1,
                    binding.sectionHowToPlay.legendCorrectLetter2,
                    binding.sectionHowToPlay.legendCorrectLetter3
                    ),
                binding.sectionHowToPlay.legendCorrectPips
            )),
            Pair(Constraint.MarkupType.INCLUDED, LegendMarkupViews(
                binding.sectionHowToPlay.legendPresentText,
                listOf(
                    binding.sectionHowToPlay.legendPresentLetter1,
                    binding.sectionHowToPlay.legendPresentLetter2,
                    binding.sectionHowToPlay.legendPresentLetter3
                ),
                binding.sectionHowToPlay.legendPresentPips
            )),
            Pair(Constraint.MarkupType.NO, LegendMarkupViews(
                binding.sectionHowToPlay.legendNoText,
                listOf(
                    binding.sectionHowToPlay.legendNoLetter1,
                    binding.sectionHowToPlay.legendNoLetter2,
                    binding.sectionHowToPlay.legendNoLetter3
                ),
                null
            ))
        )
    }

    private fun createGuess(length: Int, exact: Int, included: Int): Guess {
        val guess = List(length) { "A" }.joinToString("")
        val markup = List(length) { when {
            it < exact -> Constraint.MarkupType.EXACT
            it < exact + included -> Constraint.MarkupType.INCLUDED
            else -> Constraint.MarkupType.NO
        } }

        return Guess.createPerfectEvaluation(Constraint.create(guess, markup))
    }

    data class LegendMarkupViews(val textView: TextView, val letterViews: List<ViewGroup>, val pipView: ViewGroup?) {
        fun setVisibility(visibility: Int) {
            textView.visibility = visibility
            letterViews.forEach { it.visibility = visibility }
            pipView?.visibility = visibility
        }
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

            else -> {
                Timber.v("Can't adjust visibility of feature $feature to $visible")
            }
        }
    }

    private fun setFeatureMutability(feature: GameSetupContract.Feature, mutable: Boolean) {
        Timber.v("setFeatureMutability $feature is mutable $mutable")
        when (feature) {
            GameSetupContract.Feature.SEED -> seedViewHolder.bind(mutable = mutable)
            GameSetupContract.Feature.PLAYER_ROLE,
            GameSetupContract.Feature.CODE_LANGUAGE,
            GameSetupContract.Feature.CODE_EVALUATION_POLICY -> {
                Timber.v("Can't set mutability of feature $feature to $mutable")
            }
            GameSetupContract.Feature.CODE_LENGTH,
            GameSetupContract.Feature.CODE_CHARACTERS,
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION,
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
        val gameInfoExplanation = when {
            gameSetup.vocabulary.length > 9 -> getString(R.string.game_info_explanation_short, templateLanguageCode, templateCode)
            gameSetup.board.rounds in 1..100 -> getString(R.string.game_info_explanation, templateLanguageCode, templateCode, gameSetup.vocabulary.length, gameSetup.board.rounds)
            else -> getString(R.string.game_info_explanation_unlimited, templateLanguageCode, templateCode, gameSetup.vocabulary.length)
        }
        val gameFeedbackExplanation = when (gameSetup.evaluation.type) {
            ConstraintPolicy.AGGREGATED_EXACT -> getString(R.string.game_info_explanation_feedback_aggregated_exact)
            ConstraintPolicy.AGGREGATED_INCLUDED -> getString(R.string.game_info_explanation_feedback_aggregated_included)
            ConstraintPolicy.AGGREGATED -> getString(R.string.game_info_explanation_feedback_aggregated)
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> getString(R.string.game_info_explanation_feedback_by_letter)
            else -> throw IllegalArgumentException("Can't give Feedback explanation for policy ${gameSetup.evaluation.type}")
        }
        binding.sectionHowToPlay.gameInfoExplanation.text = getString(
            R.string.game_info_explanation_full,
            gameInfoExplanation,
            gameFeedbackExplanation
        )

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
        defaultAvailability: GameSetupContract.Availability?
    ) {
        // iterate through all features, not just those provided
        for (feature in GameSetupContract.Feature.entries) {
            val availability = availabilities[feature] ?: defaultAvailability
            if (availability != null) setFeatureAvailability(feature, availability, qualifiers[feature])
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
            GameSetupContract.Qualifier.VERSION_CHECK_FAILED -> {
                // TODO note that a version check failed? Probably not necessary for GameInfo
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

    override fun setLanguagesAllowed(languages: List<CodeLanguage>) {
        // ignore; languages cannot be changed
    }

    override fun setEvaluationPoliciesAllowed(policies: List<ConstraintPolicy>) {
        // ignore; evaluation policies cannot be changed
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}