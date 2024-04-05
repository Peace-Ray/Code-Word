package com.peaceray.codeword.presentation.view.fragments

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.GameSetupBinding
import com.peaceray.codeword.data.manager.genie.GenieGameSetupSettingsManager
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.datamodel.Information
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewListenerAdapter
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewSeedViewHolder
import com.peaceray.codeword.presentation.view.component.viewholders.review.GameReviewViewHolder
import dagger.hilt.android.AndroidEntryPoint
import okhttp3.internal.toImmutableMap
import timber.log.Timber
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class GameSetupFragment: Fragment(R.layout.game_setup), GameSetupContract.View {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameSetupFragment"
        const val ARG_GAME_TYPE = "${NAME}_GAME_TYPE"
        const val ARG_GAME_TYPE_QUALIFIERS = "${NAME}_GAME_TYPE_QUALIFIERS"

        fun newInstance(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier>? = emptySet()): GameSetupFragment {
            val fragment = GameSetupFragment()

            val args = Bundle()
            args.putString(ARG_GAME_TYPE, type.name)
            args.putStringArray(ARG_GAME_TYPE_QUALIFIERS, qualifiers?.map { it.name }?.toTypedArray() ?: emptyArray())

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onLaunchAvailabilityChanged(fragment: GameSetupFragment, available: Boolean)
        fun onSetupFinished(fragment: GameSetupFragment, seed: String?, gameSetup: GameSetup)
        fun onSetupCanceled(fragment: GameSetupFragment)
    }

    fun isLaunchAvailable() = launchEnabled

    /**
     * Respond to a button press on a "launch" button which exists outside of this Fragment.
     */
    fun onLaunchButtonClicked() {
        if (hasPresenter) presenter.onLaunchButtonClicked()
    }

    /**
     * Respond to a button press on a "cancel" button which exists outside of this Fragment.
     */
    fun onCancelButtonClicked() {
        if (hasPresenter) presenter.onCancelButtonClicked()
    }

    fun onTypeChanged(type: GameSetupContract.Type, qualifiers: Set<GameSetupContract.Qualifier> = emptySet()) {
        Timber.d("feature: onTypeChanged $type $qualifiers")
        this.type = type
        this.qualifiers = qualifiers

        if (hasPresenter) presenter.onTypeSelected(type, qualifiers)
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: GameSetupBinding? = null
    private val binding get() = _binding!!
    private val hasPresenter get() = this::presenter.isInitialized

    // ViewHolder section wrappers
    lateinit var seedViewHolder: GameReviewSeedViewHolder

    @Inject lateinit var inflater: LayoutInflater
    var listener: OnInteractionListener? = null

    @Inject lateinit var colorManager: ColorSwatchManager
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var genie: GenieGameSetupSettingsManager

    private lateinit var type: GameSetupContract.Type
    private lateinit var qualifiers: Set<GameSetupContract.Qualifier>

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

        seed = null
        gameSetup = null
        gameProgress = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // set type and qualifiers: from saved state, arguments, or default.
        type = if (this::type.isInitialized) type else GameSetupContract.Type.valueOf(
            savedInstanceState?.getString(ARG_GAME_TYPE)
                ?: arguments?.getString(ARG_GAME_TYPE)
                ?: "SEEDED"
        )

        qualifiers = if (this::qualifiers.isInitialized) qualifiers else  (
                savedInstanceState?.getStringArray(ARG_GAME_TYPE_QUALIFIERS)
                    ?: arguments?.getStringArray(ARG_GAME_TYPE_QUALIFIERS)
                    ?: emptyArray<String>()
                )
            .map { GameSetupContract.Qualifier.valueOf(it) }
            .toSet()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = GameSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // set background and other manual colors
        binding.mainView.setBackgroundColor(colorManager.colorSwatch.container.background)

        // apply spinner settings
        binding.sectionPuzzleType.playerRoleSpinner.adapter = playerRoleAdapter
        binding.sectionPuzzleType.playerRoleSpinner.onItemSelectedListener = playerRoleListener

        // apply code language settings
        binding.sectionPuzzleType.codeLanguageSpinner.adapter = codeLanguageAdapter
        binding.sectionPuzzleType.codeLanguageSpinner.onItemSelectedListener = codeLanguageListener

        // apply feedback constraint policy settings
        binding.sectionPuzzleType.feedbackPolicySpinner.adapter = feedbackPolicyAdapter
        binding.sectionPuzzleType.feedbackPolicySpinner.onItemSelectedListener = feedbackPolicyListener


        // apply code length / chars settings
        binding.sectionPuzzleType.codeLengthSeekBar.setOnSeekBarChangeListener(codeLengthListener)
        binding.sectionPuzzleType.codeCharactersSeekBar.setOnSeekBarChangeListener(codeCharactersListener)
        binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox.setOnCheckedChangeListener(codeCharacterRepetitionsListener)

        // apply opponent behavior
        binding.sectionPuzzleType.evaluatorCheatsCheckBox.setOnCheckedChangeListener(evaluatorCheatsListener)

        // apply difficulty settings
        binding.sectionDifficulty.hardModeCheckBox.setOnCheckedChangeListener(hardModeListener)
        binding.sectionDifficulty.limitedRoundsCheckBox.setOnCheckedChangeListener(roundLimitedListener)
        binding.sectionDifficulty.roundsSeekBar.setOnSeekBarChangeListener(roundsListener)

        // apply checkbox descriptions
        binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox.setPrompt(R.string.game_setup_code_character_repetitions_prompt, R.string.game_setup_code_character_repetitions_hint)
        binding.sectionPuzzleType.evaluatorCheatsCheckBox.setPrompt(R.string.game_setup_evaluator_cheats_prompt, R.string.game_setup_evaluator_cheats_hint)
        binding.sectionDifficulty.hardModeCheckBox.setPrompt(R.string.game_setup_hard_mode_prompt, R.string.game_setup_hard_mode_description)
        binding.sectionDifficulty.limitedRoundsCheckBox.setPrompt(R.string.game_setup_limited_rounds_prompt)

        // create view holders
        seedViewHolder = GameReviewSeedViewHolder(binding.sectionSeed.mainViewSeed, colorManager, gameSetupListener)

        // attach to presenter for logic
        attach(presenter)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // fast-forward check boxes to avoid distracting animation on tab switch
        binding.sectionPuzzleType.evaluatorCheatsCheckBox.jumpDrawablesToCurrentState()
        binding.sectionDifficulty.hardModeCheckBox.jumpDrawablesToCurrentState()
        binding.sectionDifficulty.limitedRoundsCheckBox.jumpDrawablesToCurrentState()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Listeners, Adapters, View Helpers
    //---------------------------------------------------------------------------------------------
    private var codeLengthsAllowed: List<Int> = listOf()
    private var codeCharactersAllowed: List<Int> = listOf()
    private var roundsAllowed: List<Int> = listOf()
    private var unlimitedRoundsAllowed = false

    private val gameSetupListener = object: GameReviewListenerAdapter() {
        override fun onRandomizeSeedClicked(
            seed: String?,
            viewHolder: GameReviewViewHolder?
        ) {
            presenter.onSeedRandomized()
        }

        override fun onEditSeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
            val builder = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_CodeWord_AlertDialog)

            builder.setTitle(R.string.game_setup_seed_prompt)
            builder.setMessage(R.string.game_setup_seed_prompt_detail)

            val view = inflater.inflate(R.layout.game_setup_seed_entry, null)
            val editText = view.findViewById<EditText>(R.id.seed)
            editText.setText(seed)

            builder.setView(view)
            builder.setPositiveButton(
                R.string.game_setup_seed_confirm,
                object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, p1: Int) {
                        val newSeed = editText.text.toString()
                        if (presenter.onSeedEntered(newSeed)) {
                            dialog?.dismiss()
                        } else {
                            editText.setText(seed)
                        }
                    }
                }
            )
            builder.setNegativeButton(
                R.string.game_setup_seed_cancel,
                object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, p1: Int) {
                        dialog?.cancel()
                    }
                }
            )
            builder.setCancelable(true)
            builder.show()

            editText.setSelection(0, seed?.length ?: 0)
            editText.postDelayed({
                editText.requestFocus()
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }, 400)
        }

        override fun onCopySeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
            // copy to clipboard
            if (seed != null) {
                val clip = ClipData.newPlainText(getString(R.string.clip_seed_label), seed)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(context, getString(R.string.clip_seed_toast), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val playerRoleAdapter: ArrayAdapter<CharSequence> by lazy {
        ArrayAdapter.createFromResource(
            requireContext(),
            R.array.game_setup_player_role_values,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private val playerRoleListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            val role: String = parent.getItemAtPosition(pos) as String
            val (solver, evaluator) = getPlayerRolesFromSpinnerItem(
                role,
                binding.sectionPuzzleType.evaluatorCheatsCheckBox.isChecked
            )

            if (!presenter.onRolesEntered(solver, evaluator)) {
                gameSetup?.let {
                    val item = getSpinnerItemFromPlayerRoles(it.solver, it.evaluator)
                    binding.sectionPuzzleType.playerRoleSpinner.setSelection(playerRoleAdapter.getPosition(item))
                }
            }
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            Timber.v("onNothingSelected")
        }
    }

    private fun getPlayerRolesFromSpinnerItem(item: String, cheat: Boolean = false): Pair<GameSetup.Solver, GameSetup.Evaluator> {
        val context = requireContext()
        val evalBot = if (cheat) {
            GameSetup.Evaluator.CHEATER
        } else {
            GameSetup.Evaluator.HONEST
        }

        val roles = when (item) {
            context.getString(R.string.game_setup_player_role_value_solver) ->
                Pair(GameSetup.Solver.PLAYER, evalBot)
            context.getString(R.string.game_setup_player_role_value_evaluator) ->
                Pair(GameSetup.Solver.BOT, GameSetup.Evaluator.PLAYER)
            context.getString(R.string.game_setup_player_role_value_both) ->
                Pair(GameSetup.Solver.PLAYER, GameSetup.Evaluator.PLAYER)
            else -> Pair(GameSetup.Solver.BOT, evalBot)
        }
        Timber.v("getPlayerRolesFromSpinnerItem $item cheat $cheat resolved to $roles")
        return roles
    }

    private fun getSpinnerItemFromPlayerRoles(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): String {
        val context = requireContext()
        val item = when {
            solver == GameSetup.Solver.PLAYER && evaluator == GameSetup.Evaluator.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_both)
            solver == GameSetup.Solver.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_solver)
            evaluator == GameSetup.Evaluator.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_evaluator)
            else -> context.getString(R.string.game_setup_player_role_value_none)
        }
        Timber.v("getSpinnerItemFromPlayerRoles $solver and $evaluator is $item")
        return item
    }

    private val codeLanguageAdapter: ArrayAdapter<CharSequence> by lazy {
        val valArray = requireContext().resources.getStringArray(R.array.game_setup_language_values)
        val valList: MutableList<CharSequence> = valArray.toMutableList()

        ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            valList
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private val codeLanguageListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            val langValue: String = parent.getItemAtPosition(pos) as String
            val language = getCodeLanguageFromSpinnerItem(langValue)

            if (!presenter.onLanguageEntered(language)) {
                gameSetup?.let {
                    val item = getSpinnerItemFromCodeLanguage(it.vocabulary.language)
                    binding.sectionPuzzleType.codeLanguageSpinner.setSelection(codeLanguageAdapter.getPosition(item))
                }
            }
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            Timber.v("onNothingSelected")
        }
    }

    private fun getCodeLanguageFromSpinnerItem(item: String): CodeLanguage {
        val context = requireContext()
        return when (item) {
            context.getString(R.string.game_setup_language_english) -> CodeLanguage.ENGLISH
            context.getString(R.string.game_setup_language_code) -> CodeLanguage.CODE
            else -> CodeLanguage.ENGLISH
        }
    }

    private fun getSpinnerItemFromCodeLanguage(language: CodeLanguage): String {
        val context = requireContext()
        return when (language) {
            CodeLanguage.ENGLISH -> context.getString(R.string.game_setup_language_english)
            CodeLanguage.CODE -> context.getString(R.string.game_setup_language_code)
        }
    }

    private val feedbackPolicyAdapter: ArrayAdapter<CharSequence> by lazy {
        val valArray = requireContext().resources.getStringArray(R.array.game_setup_feedback_values)
        val valList: MutableList<CharSequence> = valArray.toMutableList()

        ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            valList
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
    }

    private val feedbackPolicyListener = object: AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
            val policyValue: String = parent.getItemAtPosition(pos) as String
            val policy = getConstraintPolicyFromSpinnerItem(policyValue)

            if (!presenter.onConstraintPolicyEntered(policy)) {
                gameSetup?.let {
                    val item = getSpinnerItemFromConstraintPolicy(it.evaluation.type)
                    binding.sectionPuzzleType.feedbackPolicySpinner.setSelection(feedbackPolicyAdapter.getPosition(item))
                }
            }
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {
            Timber.v("onNothingSelected")
        }
    }

    private fun getConstraintPolicyFromSpinnerItem(item: String): ConstraintPolicy {
        val context = requireContext()
        return when (item) {
            context.getString(R.string.game_setup_feedback_perfect) -> ConstraintPolicy.PERFECT
            context.getString(R.string.game_setup_feedback_aggregated_counts) -> ConstraintPolicy.AGGREGATED
            context.getString(R.string.game_setup_feedback_included_counts) -> ConstraintPolicy.AGGREGATED_INCLUDED
            context.getString(R.string.game_setup_feedback_exacts_counts) -> ConstraintPolicy.AGGREGATED_EXACT
            else -> throw IllegalArgumentException("Policy string $item not supported")
        }
    }

    private fun getSpinnerItemFromConstraintPolicy(policy: ConstraintPolicy): String {
        val context = requireContext()
        return when (policy) {
            ConstraintPolicy.PERFECT -> context.getString(R.string.game_setup_feedback_perfect)
            ConstraintPolicy.AGGREGATED_EXACT -> context.getString(R.string.game_setup_feedback_exacts_counts)
            ConstraintPolicy.AGGREGATED_INCLUDED -> context.getString(R.string.game_setup_feedback_included_counts)
            ConstraintPolicy.AGGREGATED -> context.getString(R.string.game_setup_feedback_aggregated_counts)
            else -> throw IllegalArgumentException("Policy $policy not supported")
        }
    }

    private val codeLengthListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val length = codeLengthsAllowed[progress]
            binding.sectionPuzzleType.codeLengthPrompt.text = getString(R.string.game_setup_code_length_prompt, length)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            // no effect
        }

        override fun onStopTrackingTouch(seekbar: SeekBar?) {
            if (seekbar != null) {
                presenter.onFeatureEntered(
                    GameSetupContract.Feature.CODE_LENGTH,
                    codeLengthsAllowed[seekbar.progress]
                )
            }
        }
    }

    private val codeCharactersListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val chars = codeCharactersAllowed[progress]
            binding.sectionPuzzleType.codeCharactersPrompt.text = getString(R.string.game_setup_code_characters_prompt, chars)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) {
            // no effect
        }

        override fun onStopTrackingTouch(seekbar: SeekBar?) {
            if (seekbar != null) {
                presenter.onFeatureEntered(
                    GameSetupContract.Feature.CODE_CHARACTERS,
                    codeCharactersAllowed[seekbar.progress]
                )
            }
        }
    }

    private val codeCharacterRepetitionsListener = object: CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(button: CompoundButton?, checked: Boolean) {
            Timber.v("codeCharacterRepetitionsCheckBox checked to $checked")
            if (!presenter.onFeatureEntered(GameSetupContract.Feature.CODE_CHARACTER_REPETITION, checked)) {
                button?.isChecked = !checked
            }
        }
    }

    private val roundsListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val rounds = roundsAllowed[progress]
            binding.sectionDifficulty.roundsPrompt.text = getString(R.string.game_setup_rounds_prompt, rounds)
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

    private val evaluatorCheatsListener = object: CompoundButton.OnCheckedChangeListener {
        override fun onCheckedChanged(button: CompoundButton?, checked: Boolean) {
            if (!presenter.onFeatureEntered(GameSetupContract.Feature.EVALUATOR_HONEST, !checked)) {
                button?.isChecked = !checked
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
            if (checked && roundsAllowed.any { it != 0 }) {
                updateRoundsLimitedUI(true)
                val rounds = roundsAllowed[binding.sectionDifficulty.roundsSeekBar.progress]
                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, rounds)
            } else {
                updateRoundsLimitedUI(false)
                presenter.onFeatureEntered(GameSetupContract.Feature.ROUNDS, 0)
            }
        }
    }

    private fun updateRoundsLimitedUI(limited: Boolean) {
        binding.sectionDifficulty.roundsContainer.visibility = if (limited) View.VISIBLE else View.GONE
    }

    private fun setFeatureMutability(feature: GameSetupContract.Feature, mutable: Boolean) {
        Timber.v("setFeatureMutability $feature is mutable $mutable")
        when (feature) {
            GameSetupContract.Feature.SEED -> seedViewHolder.bind(mutable = mutable)
            GameSetupContract.Feature.PLAYER_ROLE -> setMutability(binding.sectionPuzzleType.playerRoleSpinner, mutable)
            GameSetupContract.Feature.CODE_LANGUAGE -> setMutability(binding.sectionPuzzleType.codeLanguageSpinner, mutable)
            GameSetupContract.Feature.CODE_LENGTH -> setMutability(binding.sectionPuzzleType.codeLengthSeekBar, mutable)
            GameSetupContract.Feature.CODE_CHARACTERS -> setMutability(binding.sectionPuzzleType.codeCharactersSeekBar, mutable)
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION -> setMutability(binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox, mutable)
            GameSetupContract.Feature.CODE_EVALUATION_POLICY -> setMutability(binding.sectionPuzzleType.feedbackPolicySpinner, mutable)
            GameSetupContract.Feature.EVALUATOR_HONEST -> setMutability(binding.sectionPuzzleType.evaluatorCheatsCheckBox, mutable)
            GameSetupContract.Feature.HARD_MODE -> setMutability(binding.sectionDifficulty.hardModeCheckBox, mutable)
            GameSetupContract.Feature.ROUNDS -> {
                Timber.v("set ROUNDS mutability to ${mutable}")
                setMutability(binding.sectionDifficulty.limitedRoundsCheckBox, mutable)
                setMutability(binding.sectionDifficulty.roundsSeekBar, mutable)
            }
            GameSetupContract.Feature.LAUNCH -> {
                launchEnabled = mutable
                listener?.onLaunchAvailabilityChanged(this, mutable)
            }
        }
    }

    private fun setMutability(control: Spinner, mutable: Boolean) {
        control.isEnabled = mutable
        control.alpha = if (mutable) 1.0f else 0.7f
    }

    private fun setMutability(control: SeekBar, mutable: Boolean) {
        control.isClickable = mutable
        control.isEnabled = mutable
    }

    private fun setMutability(control: CheckBox, mutable: Boolean) {
        control.isClickable = mutable
        control.isEnabled = mutable
    }

    private fun setFeatureVisibility(feature: GameSetupContract.Feature, visible: Boolean) {
        Timber.v("setFeatureVisibility $feature is visible: $visible")
        val visibility = if (visible) View.VISIBLE else View.GONE
        when (feature) {
            GameSetupContract.Feature.SEED -> binding.sectionSeed.seed.visibility = visibility
            GameSetupContract.Feature.PLAYER_ROLE -> binding.sectionPuzzleType.playerRoleContainer.visibility = visibility
            GameSetupContract.Feature.CODE_LANGUAGE -> binding.sectionPuzzleType.codeLanguageContainer.visibility = visibility
            GameSetupContract.Feature.CODE_LENGTH -> binding.sectionPuzzleType.codeLengthContainer.visibility = visibility
            GameSetupContract.Feature.CODE_CHARACTERS -> binding.sectionPuzzleType.codeCharactersContainer.visibility = visibility
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION -> binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox.visibility = visibility
            GameSetupContract.Feature.CODE_EVALUATION_POLICY -> binding.sectionPuzzleType.feedbackPolicyContainer.visibility = visibility
            GameSetupContract.Feature.EVALUATOR_HONEST -> binding.sectionPuzzleType.evaluatorCheatsCheckBox.visibility = visibility
            GameSetupContract.Feature.HARD_MODE -> binding.sectionDifficulty.hardModeCheckBox.visibility = visibility
            GameSetupContract.Feature.ROUNDS -> binding.sectionDifficulty.roundsMetaContainer.visibility = visibility
            GameSetupContract.Feature.LAUNCH -> {
                // TODO communicate this to Listener? Set button enabled?
            }
        }

        // meta-visibility
        when (feature) {
            GameSetupContract.Feature.SEED -> {
                val anyVisible = listOf(
                    binding.sectionSeed.seed.visibility
                ).any { it == View.VISIBLE }
                binding.sectionSeed.mainViewSeed.visibility = if (anyVisible) View.VISIBLE else View.GONE
            }

            GameSetupContract.Feature.PLAYER_ROLE,
            GameSetupContract.Feature.CODE_LANGUAGE,
            GameSetupContract.Feature.CODE_LENGTH,
            GameSetupContract.Feature.CODE_CHARACTERS,
            GameSetupContract.Feature.CODE_CHARACTER_REPETITION,
            GameSetupContract.Feature.CODE_EVALUATION_POLICY,
            GameSetupContract.Feature.EVALUATOR_HONEST -> {
                val anyVisible = listOf(
                    binding.sectionPuzzleType.playerRoleContainer.visibility,
                    binding.sectionPuzzleType.codeLanguageContainer.visibility,
                    binding.sectionPuzzleType.codeLengthContainer.visibility,
                    binding.sectionPuzzleType.codeCharactersContainer.visibility,
                    binding.sectionPuzzleType.evaluatorCheatsCheckBox.visibility
                ).any { it == View.VISIBLE }
                binding.sectionPuzzleType.mainViewPuzzleType.visibility = if (anyVisible) View.VISIBLE else View.GONE
            }

            GameSetupContract.Feature.HARD_MODE,
            GameSetupContract.Feature.ROUNDS -> {
                val anyVisible = listOf(
                    binding.sectionDifficulty.hardModeCheckBox.visibility,
                    binding.sectionDifficulty.limitedRoundsCheckBox.visibility,
                    binding.sectionDifficulty.roundsMetaContainer.visibility
                ).any { it == View.VISIBLE }
                binding.sectionDifficulty.mainViewDifficulty.visibility = if (anyVisible) View.VISIBLE else View.GONE
            }

            GameSetupContract.Feature.LAUNCH -> {
                // TODO a "launch" section?
            }
        }
    }

    private fun setFeatureProgress(feature: GameSetupContract.Feature, value: Int) {
        val seekbar: SeekBar
        val label: TextView
        val labelText: String
        val values: List<Int>
        when(feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                seekbar = binding.sectionPuzzleType.codeLengthSeekBar
                label = binding.sectionPuzzleType.codeLengthPrompt
                labelText = getString(R.string.game_setup_code_length_prompt, value)
                values = codeLengthsAllowed
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                seekbar = binding.sectionPuzzleType.codeCharactersSeekBar
                label = binding.sectionPuzzleType.codeCharactersPrompt
                labelText = getString(R.string.game_setup_code_characters_prompt, value)
                values = codeCharactersAllowed
            }
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
            seekbar.progress = index
            label.text = labelText
        } else {
            label.text = labelText
        }
    }

    private fun TextView.setPrompt(@StringRes text: Int, @StringRes description: Int? = null) {
        setText(getPromptText(text, description), TextView.BufferType.SPANNABLE)
    }

    private fun TextView.setPrompt(text: String, description: String? = null) {
        setText(getPromptText(text, description), TextView.BufferType.SPANNABLE)
    }

    private fun getPromptText(@StringRes text: Int, @StringRes description: Int? = null) = getPromptText(
        getString(text),
        if (description == null) null else getString(description)
    )

    private fun getPromptText(text: String, description: String? = null): CharSequence {
        val ssb = SpannableStringBuilder(text)

        val textSize = resources.getDimension(R.dimen.text_size_subheader).toInt()
        val descriptionSize = resources.getDimension(R.dimen.text_size).toInt()
        if (description != null) {
            val start = text.length + 1
            val end = text.length + description.length + 1
            val flag = Spannable.SPAN_INCLUSIVE_EXCLUSIVE
            ssb.append("\n")
                .append(description)
            ssb.setSpan(AbsoluteSizeSpan(textSize), 0, start, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            ssb.setSpan(AbsoluteSizeSpan(descriptionSize), start, end, flag)
            ssb.setSpan(AbsoluteSizeSpan(descriptionSize), start, end, flag)
        } else {
            ssb.setSpan(AbsoluteSizeSpan(textSize), 0, text.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        }
        ssb.setSpan(ForegroundColorSpan(colorManager.colorSwatch.information.onBackground.color), 0, ssb.length, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
        return ssb
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameSetupContract.Presenter

    private var seed: String? = null
    private var gameSetup: GameSetup? = null
    private var gameProgress: GameStatusReview.Status? = null

    private var launchEnabled: Boolean = false

    override fun getType() = type

    override fun getQualifiers() = qualifiers

    override fun getOngoingGameSetup() = null

    override fun finishGameSetup(review: GameStatusReview) {
        var seed = review.seed
        var setup = review.setup
        Timber.v("finishGameSetup with seed $seed setup $setup")

        if (!genie.allowCustomSecret) {
            listener?.onSetupFinished(this, seed, setup)
        } else {
            val launch: () -> Unit = {
                listener?.onSetupFinished(this, seed, setup)
            }

            // Game Genie: allow the user to set an explicit secret! This may break things.
            val builder = AlertDialog.Builder(requireContext(), R.style.ThemeOverlay_CodeWord_AlertDialog)

            builder.setTitle(R.string.genie_set_secret_prompt)
            builder.setMessage(getString(
                R.string.genie_set_secret_prompt_detail,
                getSpinnerItemFromCodeLanguage(setup.vocabulary.language),
                setup.vocabulary.length
            ))

            val view = inflater.inflate(R.layout.genie_secret_entry, null)
            val editText = view.findViewById<EditText>(R.id.secret)
            editText.setText("")

            builder.setView(view)
            builder.setPositiveButton(
                R.string.genie_set_secret_confirm,
                object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, p1: Int) {
                        val secret = editText.text.toString()
                        if (secret.isNotBlank()) {
                            setup = setup.copy(vocabulary = setup.vocabulary.copy(secret = secret))
                        }
                        dialog?.dismiss()
                        launch()
                    }
                }
            )
            builder.setNegativeButton(
                R.string.genie_set_secret_cancel,
                object: DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, p1: Int) {
                        dialog?.cancel()
                        launch()
                    }
                }
            )
            builder.setCancelable(true)
            builder.show()

            editText.postDelayed({
                editText.requestFocus()
                val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }, 400)
        }
    }

    override fun cancelGameSetup() {
        Timber.v("cancelGameSetup")
        listener?.onSetupCanceled(this)
    }

    override fun setGameStatusReview(review: GameStatusReview) {
        Timber.d("setGameStatusReview for type ${getType()} $review")

        val seed = review.seed
        val gameSetup = review.setup
        val progress = review.status

        // set values top to bottom
        seedViewHolder.bind(review = review)

        Timber.v("set player role selection for ${gameSetup.solver} ${gameSetup.evaluator}")
        binding.sectionPuzzleType.playerRoleSpinner.setSelection(
            playerRoleAdapter.getPosition(getSpinnerItemFromPlayerRoles(
                gameSetup.solver,
                gameSetup.evaluator
            ))
        )
        binding.sectionPuzzleType.codeLanguageSpinner.setSelection(
            codeLanguageAdapter.getPosition(
                getSpinnerItemFromCodeLanguage(gameSetup.vocabulary.language)
            )
        )
        binding.sectionPuzzleType.feedbackPolicySpinner.setSelection(
            feedbackPolicyAdapter.getPosition(
                getSpinnerItemFromConstraintPolicy(gameSetup.evaluation.type)
            )
        )
        setFeatureProgress(GameSetupContract.Feature.CODE_LENGTH, gameSetup.vocabulary.length)
        setFeatureProgress(GameSetupContract.Feature.CODE_CHARACTERS, gameSetup.vocabulary.characters)
        setFeatureProgress(GameSetupContract.Feature.ROUNDS, gameSetup.board.rounds)

        Timber.v("set codeCharacterRepetitionsCheckBox to ${gameSetup.vocabulary.characterOccurrences > 1}; is enabled ${binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox.isEnabled}")
        binding.sectionPuzzleType.codeCharacterRepetitionsCheckBox.quickCheck(gameSetup.vocabulary.characterOccurrences > 1, this.gameSetup == null)
        binding.sectionPuzzleType.evaluatorCheatsCheckBox.quickCheck(gameSetup.evaluator == GameSetup.Evaluator.CHEATER, this.gameSetup == null)

        binding.sectionDifficulty.hardModeCheckBox.quickCheck(gameSetup.evaluation.enforced != ConstraintPolicy.IGNORE, this.gameSetup == null)
        binding.sectionDifficulty.limitedRoundsCheckBox.quickCheck(gameSetup.board.rounds > 0, this.gameSetup == null)

        this.seed = seed
        this.gameSetup = gameSetup
        this.gameProgress = progress

        updateInformationTooltip(review)
    }

    private fun CheckBox.quickCheck(checked: Boolean, force: Boolean = false) {
        if (isChecked != checked || force) {
            isChecked = checked
            jumpDrawablesToCurrentState()
        }
    }

    override fun showError(feature: GameSetupContract.Feature, error: GameSetupContract.Error, qualifier: GameSetupContract.Qualifier?) {
        updateInformationTooltip(feature, error, qualifier)
    }

    override fun setCodeLanguage(characters: Iterable<Char>, locale: Locale) {
        // TODO anything needed here?
    }

    override fun setCodeComposition(characters: Iterable<Char>) {
        // TODO anything needed here?
    }

    override fun setFeatureAvailability(
        availabilities: Map<GameSetupContract.Feature, GameSetupContract.Availability>,
        qualifiers: Map<GameSetupContract.Feature, GameSetupContract.Qualifier>,
        defaultAvailability: GameSetupContract.Availability?
    ) {
        // iterate through all features, not just those provided in the map
        for (feature in GameSetupContract.Feature.entries) {
            val availability = availabilities[feature] ?: defaultAvailability
            if (availability != null) onSetFeatureAvailability(feature, availability, qualifiers[feature])
        }
        // update tooltip
        updateInformationTooltip(availabilities, qualifiers)
    }

    override fun setFeatureAvailability(
        feature: GameSetupContract.Feature,
        availability: GameSetupContract.Availability,
        qualifier: GameSetupContract.Qualifier?
    ) {
        onSetFeatureAvailability(feature, availability, qualifier)
        updateInformationTooltip(feature, availability, qualifier)
    }

    private fun onSetFeatureAvailability(
        feature: GameSetupContract.Feature,
        availability: GameSetupContract.Availability,
        qualifier: GameSetupContract.Qualifier?
    ) {
        val mutable = availability == GameSetupContract.Availability.AVAILABLE
        val visible = availability != GameSetupContract.Availability.DISABLED

        setFeatureMutability(feature, mutable)
        setFeatureVisibility(feature, visible)
    }

    override fun setFeatureValuesAllowed(feature: GameSetupContract.Feature, values: List<Int>) {
        val value = when (feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                codeLengthsAllowed = values
                binding.sectionPuzzleType.codeLengthSeekBar.max = values.size - 1
                gameSetup?.vocabulary?.length
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                codeCharactersAllowed = values
                binding.sectionPuzzleType.codeCharactersSeekBar.max = values.size - 1
                gameSetup?.vocabulary?.characters
            }
            GameSetupContract.Feature.ROUNDS -> {
                unlimitedRoundsAllowed = 0 in values
                roundsAllowed = if (!unlimitedRoundsAllowed) values else {
                    val rounds = values.toMutableList()
                    rounds.remove(0)
                    rounds.toList()
                }
                binding.sectionDifficulty.roundsSeekBar.max = roundsAllowed.size - 1
                // update UI
                updateRoundsLimitedUI(
                    binding.sectionDifficulty.limitedRoundsCheckBox.isChecked
                            && roundsAllowed.isNotEmpty()
                )

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
        val languageStrings = languages.map { getSpinnerItemFromCodeLanguage(it) }
        codeLanguageAdapter.clear()
        codeLanguageAdapter.addAll(languageStrings)

        gameSetup?.let { setup ->
            val selectedItem = getSpinnerItemFromCodeLanguage(setup.vocabulary.language)
            val position = codeLanguageAdapter.getPosition(selectedItem)
            if (position >= 0) binding.sectionPuzzleType.codeLanguageSpinner.setSelection(position)
        }
    }

    override fun setEvaluationPoliciesAllowed(policies: List<ConstraintPolicy>) {
        val policyStrings = policies.map { getSpinnerItemFromConstraintPolicy(it) }
        feedbackPolicyAdapter.clear()
        feedbackPolicyAdapter.addAll(policyStrings)

        gameSetup?.let { setup ->
            val selectedItem = getSpinnerItemFromConstraintPolicy(setup.evaluation.type)
            val position = feedbackPolicyAdapter.getPosition(selectedItem)
            if (position >= 0) binding.sectionPuzzleType.feedbackPolicySpinner.setSelection(position)
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Tooltips: Error / Qualifier / Note display
    //---------------------------------------------------------------------------------------------
    private var tooltipContext: InformationTooltipContext? = null

    private fun updateInformationTooltip(review: GameStatusReview) {
        showTooltipContext(InformationTooltipContext(
            review = review,
            featureAvailable = tooltipContext?.featureAvailable ?: emptyMap(),
            featureQualifier = tooltipContext?.featureQualifier ?: emptyMap(),
            error = null,
            errorFeature = null,
            errorQualifier = null
        ))
    }

    private fun updateInformationTooltip(
        featureAvailable: Map<GameSetupContract.Feature, GameSetupContract.Availability>,
        featureQualifier: Map<GameSetupContract.Feature, GameSetupContract.Qualifier>
    ) {
        showTooltipContext(InformationTooltipContext(
            review = tooltipContext?.review,
            featureAvailable = featureAvailable,
            featureQualifier = featureQualifier,
            error = null,
            errorFeature = null,
            errorQualifier = null
        ))
    }

    private fun updateInformationTooltip(
        feature: GameSetupContract.Feature,
        availability: GameSetupContract.Availability,
        qualifier: GameSetupContract.Qualifier?
    ) {
        val featureAvailability = (tooltipContext?.featureAvailable?.toMutableMap() ?: mutableMapOf())
        val featureQualifier = (tooltipContext?.featureQualifier?.toMutableMap() ?: mutableMapOf())

        featureAvailability[feature] = availability
        if (qualifier != null) {
            featureQualifier[feature] = qualifier
        } else {
            featureQualifier.remove(feature)
        }

        showTooltipContext(InformationTooltipContext(
            review = tooltipContext?.review,
            featureAvailable = featureAvailability.toImmutableMap(),
            featureQualifier = featureQualifier.toImmutableMap(),
            error = null,
            errorFeature = null,
            errorQualifier = null
        ))
    }

    private fun updateInformationTooltip(feature: GameSetupContract.Feature, error: GameSetupContract.Error, qualifier: GameSetupContract.Qualifier?) {
        showTooltipContext(InformationTooltipContext(
            review = tooltipContext?.review,
            featureAvailable = tooltipContext?.featureAvailable ?: emptyMap(),
            featureQualifier = tooltipContext?.featureQualifier ?: emptyMap(),
            error = error,
            errorFeature = feature,
            errorQualifier = qualifier
        ))
    }

    private fun showTooltipContext(tooltipContext: InformationTooltipContext) {
        // TODO support errors that fade away, or are replaced with general state info
        Timber.v("showTooltipContext for ${tooltipContext.information}: ${tooltipContext.tooltip}")
        if (tooltipContext.tooltip != null) {
            binding.sectionWarnings.mainViewWarnings.visibility = View.VISIBLE
            binding.sectionWarnings.warningsTextView.text = tooltipContext.tooltip
            binding.sectionWarnings.warningsTextView.setTextColor(
                colorManager.colorSwatch.information.onBackground.color(tooltipContext.information)
            )
        } else {
            binding.sectionWarnings.mainViewWarnings.visibility = View.GONE
        }
    }

    private inner class InformationTooltipContext(
        val review: GameStatusReview?,
        val featureAvailable: Map<GameSetupContract.Feature, GameSetupContract.Availability>,
        val featureQualifier: Map<GameSetupContract.Feature, GameSetupContract.Qualifier>,
        val error: GameSetupContract.Error?,
        val errorFeature: GameSetupContract.Feature?,
        val errorQualifier: GameSetupContract.Qualifier?
    ) {
        val information: Information?
        val tooltip: String?
        val daily = when {
            review?.setup?.daily == true || type == GameSetupContract.Type.DAILY -> true
            review?.setup?.daily == false || type in setOf(GameSetupContract.Type.SEEDED, GameSetupContract.Type.CUSTOM) -> false
            else -> null
        }

        init {
            // set information and tooltip
            when {
                error != null -> {
                    information = error
                    tooltip = when (information) {
                        GameSetupContract.Error.GAME_EXPIRED -> context?.getString(R.string.game_setup_error_game_expired)
                        GameSetupContract.Error.GAME_FORTHCOMING -> context?.getString(R.string.game_setup_error_game_forthcoming)
                        GameSetupContract.Error.GAME_COMPLETE -> context?.getString(R.string.game_setup_error_game_complete)
                        GameSetupContract.Error.FEATURE_NOT_ALLOWED -> when (errorFeature) {
                            GameSetupContract.Feature.LAUNCH -> getStringForLaunchQualifier(errorQualifier)
                            else -> context?.getString(R.string.game_setup_error_feature_not_allowed)
                        }
                        GameSetupContract.Error.FEATURE_VALUE_INVALID -> when (errorFeature) {
                            GameSetupContract.Feature.SEED -> context?.getString(R.string.game_setup_error_feature_value_invalid_seed)
                            else -> context?.getString(R.string.game_setup_error_feature_value_invalid)
                        }
                        GameSetupContract.Error.FEATURE_VALUE_NOT_ALLOWED -> when (errorFeature) {
                            GameSetupContract.Feature.SEED -> context?.getString(R.string.game_setup_error_feature_value_not_allowed_seed)
                            else -> context?.getString(R.string.game_setup_error_feature_value_not_allowed)
                        }
                    }
                }
                featureQualifier[GameSetupContract.Feature.LAUNCH] != null -> {
                    information = featureQualifier[GameSetupContract.Feature.LAUNCH]
                    tooltip = getStringForLaunchQualifier(information)
                }
                review?.notes?.isNotEmpty() == true -> {
                    information = review.notes.maxByOrNull { it.level.priority }
                    tooltip = when (information) {
                        GameStatusReview.Note.SEED_LEGACY -> context?.getString(R.string.game_setup_note_seed_legacy)
                        GameStatusReview.Note.SEED_RETIRED -> context?.getString(R.string.game_setup_note_seed_retired)
                        GameStatusReview.Note.SEED_FUTURISTIC -> context?.getString(R.string.game_setup_note_seed_futuristic)
                        GameStatusReview.Note.SEED_ERA_UNDETERMINED -> context?.getString(R.string.game_setup_note_seed_undetermined)
                        GameStatusReview.Note.GAME_EXPIRED -> context?.getString(R.string.game_setup_note_game_expired)
                        GameStatusReview.Note.GAME_FORTHCOMING -> context?.getString(R.string.game_setup_note_game_forthcoming)
                        GameStatusReview.Note.GAME_LOCAL_ONLY -> context?.getString(R.string.game_setup_note_game_local_daily)
                        null -> null
                    }
                }
                else -> {
                    information = null
                    tooltip = null
                }
            }
        }

        private fun getStringForLaunchQualifier(qualifier: GameSetupContract.Qualifier?): String? {
            return if (daily == null) {
                context?.getString(R.string.game_setup_error_unknown)
            } else {
                when (qualifier) {
                    GameSetupContract.Qualifier.VERSION_CHECK_PENDING -> context?.getString(
                        if (daily) R.string.game_setup_qualifier_version_pending_daily
                        else R.string.game_setup_qualifier_version_pending_seeded
                    )

                    GameSetupContract.Qualifier.VERSION_CHECK_FAILED -> context?.getString(
                        if (daily) R.string.game_setup_qualifier_version_failed_daily
                        else R.string.game_setup_qualifier_version_failed_seeded
                    )

                    GameSetupContract.Qualifier.VERSION_UPDATE_AVAILABLE -> context?.getString(
                        if (daily) R.string.game_setup_qualifier_version_update_available_daily
                        else R.string.game_setup_qualifier_version_update_available_seeded
                    )

                    GameSetupContract.Qualifier.VERSION_UPDATE_RECOMMENDED -> context?.getString(
                        if (daily) R.string.game_setup_qualifier_version_update_recommended_daily
                        else R.string.game_setup_qualifier_version_update_recommended_seeded
                    )

                    GameSetupContract.Qualifier.VERSION_UPDATE_REQUIRED -> context?.getString(
                        if (daily) R.string.game_setup_qualifier_version_update_required_daily
                        else R.string.game_setup_qualifier_version_update_required_seeded
                    )

                    GameSetupContract.Qualifier.LOCAL_DAILY -> null

                    null -> null
                }
            }
        }
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}