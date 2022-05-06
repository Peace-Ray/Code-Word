package com.peaceray.codeword.presentation.view.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.GameInfoBinding
import com.peaceray.codeword.databinding.GameSetupBinding
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GuessLetter
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.adapters.GuessLetterAdapter
import com.peaceray.codeword.presentation.view.component.viewholders.GuessLetterViewHolder
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GameInfoFragment: Fragment(R.layout.game_info), GameSetupContract.View {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameInfoFragment"
        const val ARG_GAME_SEED = "${NAME}_GAME_SEED"
        const val ARG_GAME_SETUP = "${NAME}_GAME_SETUP"

        fun newInstance(seed: String?, setup: GameSetup): GameInfoFragment {
            val fragment = GameInfoFragment()

            val args = Bundle()
            args.putString(ARG_GAME_SEED, seed)
            args.putParcelable(ARG_GAME_SETUP, setup)

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onInfoFinished(fragment: GameInfoFragment, seed: String?, gameSetup: GameSetup)
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

    // Legend recycler controls
    @Inject lateinit var legendAdapter: GuessLetterAdapter
    lateinit var legendLayoutManager: RecyclerView.LayoutManager

    // Legend ViewHolders
    private lateinit var legendCorrectVH: GuessLetterViewHolder
    private lateinit var legendPresentVH: GuessLetterViewHolder
    private lateinit var legendNo1VH: GuessLetterViewHolder
    private lateinit var legendNo2VH: GuessLetterViewHolder

    // Legend Data
    private lateinit var legendGuess: String
    private lateinit var legendSecret: String

    // manager / inflater
    @Inject lateinit var inflater: LayoutInflater
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var colorSwatchManager: ColorSwatchManager

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
        _binding = GameInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // apply seed tap behavior
        binding.seed.setOnClickListener(seedClickListener)

        // setup legend recycler view with blanks
        binding.recyclerView.adapter = legendAdapter
        binding.recyclerView.itemAnimator = GuessLetterViewHolder.ItemAnimator()

        legendGuess = getString(R.string.game_info_legend_guess)
        legendSecret = getString(R.string.game_info_legend_secret)

        legendLayoutManager = GridLayoutManager(context, legendGuess.length)
        binding.recyclerView.layoutManager = legendLayoutManager
        legendAdapter.setGameFieldSize(legendGuess.length, 2)

        // setup legend view holders
        legendCorrectVH = createLetterViewHolder(binding.legendCorrectLetter.getChildAt(0))
        legendPresentVH = createLetterViewHolder(binding.legendPresentLetter.getChildAt(0))
        legendNo1VH = createLetterViewHolder(binding.legendNoLetter1.getChildAt(0))
        legendNo2VH = createLetterViewHolder(binding.legendNoLetter2.getChildAt(0))

        // TODO apply animation to these
        legendAdapter.setConstraints(listOf(
            Constraint.Companion.create(legendGuess, legendSecret),
            Constraint.Companion.create(legendSecret, legendSecret)
        ))
        legendCorrectVH.bind(GuessLetter(getString(R.string.game_info_legend_correct_letter)[0], Constraint.MarkupType.EXACT))
        legendPresentVH.bind(GuessLetter(getString(R.string.game_info_legend_present_letter)[0], Constraint.MarkupType.INCLUDED))
        legendNo1VH.bind(GuessLetter(getString(R.string.game_info_legend_no_letter_1)[0], Constraint.MarkupType.NO))
        legendNo2VH.bind(GuessLetter(getString(R.string.game_info_legend_no_letter_2)[0], Constraint.MarkupType.NO))

        // apply difficulty settings
        binding.hardModeCheckBox.setOnCheckedChangeListener(hardModeListener)
        binding.limitedRoundsCheckBox.setOnCheckedChangeListener(roundLimitedListener)
        binding.roundsSeekBar.setOnSeekBarChangeListener(roundsListener)

        // view colors
        colorSwatchManager.colorSwatchLiveData.observe(viewLifecycleOwner) { updateViewColors(it) }

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun createLetterViewHolder(itemView: View) = GuessLetterViewHolder(
        itemView,
        layoutInflater,
        colorSwatchManager
    )
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Listeners, Adapters, View Helpers
    //---------------------------------------------------------------------------------------------
    private var roundsAllowed: List<Int> = listOf()
    private var unlimitedRoundsAllowed = false

    private val seedClickListener = object: View.OnClickListener {
        override fun onClick(p0: View?) {
            // copy to clipboard
            val clip = ClipData.newPlainText(getString(R.string.clip_seed_label), binding.seed.text)
            clipboardManager.setPrimaryClip(clip)
            Toast.makeText(context, getString(R.string.clip_seed_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private val roundsListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val chars = roundsAllowed[progress]
            binding.roundsValue.text = "$chars"
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            // no effect
        }

        override fun onStopTrackingTouch(seekbar: SeekBar?) {
            if (seekbar != null) {
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
                binding.roundsContainer.visibility = View.VISIBLE

                val rounds = roundsAllowed[binding.roundsSeekBar.progress]
                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, rounds)
            } else {
                binding.roundsContainer.visibility = View.GONE

                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, 0)
            }
        }
    }

    private fun setFeatureVisibility(feature: GameSetupContract.Feature, visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        when (feature) {
            GameSetupContract.Feature.SEED -> binding.seed.visibility = visibility
            GameSetupContract.Feature.HARD_MODE -> binding.hardModeCheckBox.visibility = visibility
            GameSetupContract.Feature.ROUNDS -> binding.roundsContainer.visibility = visibility
            GameSetupContract.Feature.LAUNCH -> {
                // TODO communicate this to Listener? Set button enabled?
            }
        }
    }

    private fun setFeatureProgress(feature: GameSetupContract.Feature, value: Int) {
        val seekbar: SeekBar
        val label: TextView
        val values: List<Int>
        when(feature) {
            GameSetupContract.Feature.ROUNDS -> {
                seekbar = binding.roundsSeekBar
                label = binding.roundsValue
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
            label.text = "$value"
        } else {
            seekbar.isEnabled = false
            label.text = "$value"
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region View helpers
    //---------------------------------------------------------------------------------------------
    private fun updateViewColors(swatch: ColorSwatch) {
        binding.mainView.setBackgroundColor(swatch.container.background)
        binding.recyclerView.setBackgroundColor(swatch.container.background)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameSetupContract.Presenter

    private var seed: String? = null
    private var gameSetup: GameSetup? = null
    private var gameProgress: GameSetupContract.SessionProgress? = null

    override fun getType(): GameSetupContract.Type = GameSetupContract.Type.ONGOING

    override fun getOngoingGameSetup(): Pair<String?, GameSetup>? {
        val bundle = requireArguments()
        val seed = bundle.getString(ARG_GAME_SEED)
        val setup = bundle.getParcelable<GameSetup>(ARG_GAME_SETUP)
        return Pair(seed, setup!!)
    }

    override fun finishGameSetup(seed: String?, setup: GameSetup) {
        Timber.v("finishGameSetup with seed $seed setup $setup")
        listener?.onInfoFinished(this, seed, setup)
    }

    override fun cancelGameSetup() {
        Timber.v("cancelGameSetup")
        listener?.onInfoCanceled(this)
    }

    override fun setGameSetup(seed: String?, gameSetup: GameSetup, progress: GameSetupContract.SessionProgress) {
        this.seed = seed
        this.gameSetup = gameSetup
        this.gameProgress = progress

        // set values top to bottom
        binding.seed.text = seed ?: ""
        setFeatureVisibility(GameSetupContract.Feature.SEED, seed != null)
        setFeatureProgress(GameSetupContract.Feature.ROUNDS, gameSetup.board.rounds)

        binding.hardModeCheckBox.isChecked = gameSetup.evaluation.enforced != ConstraintPolicy.IGNORE
        binding.limitedRoundsCheckBox.isChecked = gameSetup.board.rounds > 0
    }

    override fun showError(feature: GameSetupContract.Feature, error: GameSetupContract.Error) {
        Timber.e("Would show $feature error $error but don't know how")
    }

    override fun setFeatureAllowed(features: Collection<GameSetupContract.Feature>) {
        // iterate through all features, not just those provided
        for (feature in GameSetupContract.Feature.values()) {
            setFeatureAllowed(feature, feature in features)
        }
    }

    override fun setFeatureAllowed(feature: GameSetupContract.Feature, allowed: Boolean) {
        when(feature) {
            GameSetupContract.Feature.SEED -> {
                // seed should be displayed if known; it is never edited
            }
            else -> setFeatureVisibility(feature, allowed)
        }

    }

    override fun setFeatureValuesAvailable(feature: GameSetupContract.Feature, values: List<Int>) {
        val value = when (feature) {
            GameSetupContract.Feature.ROUNDS -> {
                unlimitedRoundsAllowed = 0 in values
                roundsAllowed = if (!unlimitedRoundsAllowed) values else {
                    val rounds = values.toMutableList()
                    rounds.remove(0)
                    rounds.toList()
                }
                binding.roundsSeekBar.max = roundsAllowed.size - 1
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