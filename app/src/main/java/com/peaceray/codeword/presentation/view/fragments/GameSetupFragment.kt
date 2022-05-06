package com.peaceray.codeword.presentation.view.fragments

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSaveData
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.databinding.GameSetupBinding
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameSetupContract
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class GameSetupFragment: Fragment(R.layout.game_setup), GameSetupContract.View {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameSetupFragment"
        const val ARG_GAME_TYPE = "${NAME}_GAME_TYPE"

        fun newInstance(type: GameSetupContract.Type): GameSetupFragment {
            val fragment = GameSetupFragment()

            val args = Bundle()
            args.putString(ARG_GAME_TYPE, type.name)

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onSetupFinished(fragment: GameSetupFragment, seed: String?, gameSetup: GameSetup)
        fun onSetupCanceled(fragment: GameSetupFragment)
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
    private var _binding: GameSetupBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var inflater: LayoutInflater
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
        _binding = GameSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // apply seed tap behavior
        binding.seed.setOnClickListener(seedClickListener)

        // apply spinner settings
        binding.playerRoleSpinner.adapter = playerRoleAdapter
        binding.playerRoleSpinner.onItemSelectedListener = playerRoleListener

        // apply code language settings
        binding.codeLanguageContainer.setOnCheckedChangeListener(codeLanguageListener)

        // apply code length / chars settings
        binding.codeLengthSeekBar.setOnSeekBarChangeListener(codeLengthListener)
        binding.codeCharactersSeekBar.setOnSeekBarChangeListener(codeCharactersListener)

        // apply difficulty settings
        binding.evaluatorCheatsCheckBox.setOnCheckedChangeListener(evaluatorCheatsListener)
        binding.hardModeCheckBox.setOnCheckedChangeListener(hardModeListener)
        binding.limitedRoundsCheckBox.setOnCheckedChangeListener(roundLimitedListener)
        binding.roundsSeekBar.setOnSeekBarChangeListener(roundsListener)

        // attach to presenter for logic
        attach(presenter)
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

    private val seedClickListener = object: View.OnClickListener {
        override fun onClick(p0: View?) {
            val builder = AlertDialog.Builder(requireContext())

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
            editText.postDelayed({ editText.requestFocus() }, 500)
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
                binding.evaluatorCheatsCheckBox.isChecked
            )

            if (!presenter.onRolesEntered(solver, evaluator)) {
                val item = getSpinnerItemFromPlayerRoles(gameSetup!!.solver, gameSetup!!.evaluator)
                binding.playerRoleSpinner.setSelection(playerRoleAdapter.getPosition(item))
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

        return when (item) {
            context.getString(R.string.game_setup_player_role_value_solver) ->
                Pair(GameSetup.Solver.PLAYER, evalBot)
            context.getString(R.string.game_setup_player_role_value_evaluator) ->
                Pair(GameSetup.Solver.BOT, GameSetup.Evaluator.PLAYER)
            context.getString(R.string.game_setup_player_role_value_both) ->
                Pair(GameSetup.Solver.PLAYER, GameSetup.Evaluator.PLAYER)
            else -> Pair(GameSetup.Solver.BOT, evalBot)
        }
    }

    private fun getSpinnerItemFromPlayerRoles(solver: GameSetup.Solver, evaluator: GameSetup.Evaluator): String {
        val context = requireContext()
        return when {
            solver == GameSetup.Solver.PLAYER && evaluator == GameSetup.Evaluator.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_both)
            solver == GameSetup.Solver.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_solver)
            evaluator == GameSetup.Evaluator.PLAYER ->
                context.getString(R.string.game_setup_player_role_value_evaluator)
            else -> context.getString(R.string.game_setup_player_role_value_none)
        }
    }

    private val codeLanguageListener = object: RadioGroup.OnCheckedChangeListener {
        override fun onCheckedChanged(radioGroup: RadioGroup?, checkedId: Int) {
            presenter.onLanguageEntered(getCodeLanguageFromRadioButtonId(checkedId))
        }
    }

    private fun getCodeLanguageFromRadioButtonId(id: Int) = when(id) {
        R.id.codeLanguageButtonEnglish -> CodeLanguage.ENGLISH
        R.id.codeLanguageButtonCode -> CodeLanguage.CODE
        else -> throw IllegalArgumentException("Unrecognized RadioButton id $id")
    }

    private fun getRadioButtonIdFromCodeLanguage(codeLanguage: CodeLanguage) = when(codeLanguage) {
        CodeLanguage.ENGLISH -> R.id.codeLanguageButtonEnglish
        CodeLanguage.CODE -> R.id.codeLanguageButtonCode
    }

    private val codeLengthListener = object: SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, p2: Boolean) {
            val length = codeLengthsAllowed[progress]
            binding.codeLengthValue.text = "$length"
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
            binding.codeCharactersValue.text = "$chars"
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
            GameSetupContract.Feature.PLAYER_ROLE -> binding.playerRoleContainer.visibility = visibility
            GameSetupContract.Feature.CODE_LANGUAGE -> binding.codeLanguageContainer.visibility = visibility
            GameSetupContract.Feature.CODE_LENGTH -> binding.codeLengthContainer.visibility = visibility
            GameSetupContract.Feature.CODE_CHARACTERS -> binding.codeCharactersContainer.visibility = visibility
            GameSetupContract.Feature.EVALUATOR_HONEST -> binding.evaluatorCheatsCheckBox.visibility = visibility
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
            GameSetupContract.Feature.CODE_LENGTH -> {
                seekbar = binding.codeLengthSeekBar
                label = binding.codeLengthValue
                values = codeLengthsAllowed
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                seekbar = binding.codeCharactersSeekBar
                label = binding.codeCharactersValue
                values = codeCharactersAllowed
            }
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


    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameSetupContract.Presenter

    private var seed: String? = null
    private var gameSetup: GameSetup? = null
    private var gameProgress: GameSetupContract.SessionProgress? = null

    override fun getType(): GameSetupContract.Type = GameSetupContract.Type.valueOf(
        arguments?.getString(ARG_GAME_TYPE) ?: "SEEDED"
    )

    override fun getOngoingGameSetup() = null

    override fun finishGameSetup(seed: String?, setup: GameSetup) {
        Timber.v("finishGameSetup with seed $seed setup $setup")
        listener?.onSetupFinished(this, seed, setup)
    }

    override fun cancelGameSetup() {
        Timber.v("cancelGameSetup")
        listener?.onSetupCanceled(this)
    }

    override fun setGameSetup(seed: String?, gameSetup: GameSetup, progress: GameSetupContract.SessionProgress) {
        this.seed = seed
        this.gameSetup = gameSetup
        this.gameProgress = progress

        // set values top to bottom
        binding.seed.setText(seed ?: "")
        binding.playerRoleSpinner.setSelection(
            playerRoleAdapter.getPosition(getSpinnerItemFromPlayerRoles(
                gameSetup.solver,
                gameSetup.evaluator
            ))
        )
        binding.codeLanguageContainer.check(getRadioButtonIdFromCodeLanguage(gameSetup.vocabulary.language))
        setFeatureProgress(GameSetupContract.Feature.CODE_LENGTH, gameSetup.vocabulary.length)
        setFeatureProgress(GameSetupContract.Feature.CODE_CHARACTERS, gameSetup.vocabulary.characters)
        setFeatureProgress(GameSetupContract.Feature.ROUNDS, gameSetup.board.rounds)

        binding.evaluatorCheatsCheckBox.isChecked = gameSetup.evaluator == GameSetup.Evaluator.CHEATER
        binding.hardModeCheckBox.isChecked = gameSetup.evaluation.enforced != ConstraintPolicy.IGNORE
        binding.limitedRoundsCheckBox.isChecked = gameSetup.board.rounds > 0
    }

    override fun showError(feature: GameSetupContract.Feature, error: GameSetupContract.Error) {
        Timber.e("Would show $feature error $error but don't know how")
    }

    override fun setFeatureAllowed(features: Collection<GameSetupContract.Feature>) {
        // iterate through all features, not just those provided
        for (feature in GameSetupContract.Feature.values()) {
            setFeatureVisibility(feature, feature in features)
        }
    }

    override fun setFeatureAllowed(feature: GameSetupContract.Feature, allowed: Boolean) {
        setFeatureVisibility(feature, allowed)
    }

    override fun setFeatureValuesAvailable(feature: GameSetupContract.Feature, values: List<Int>) {
        val value = when (feature) {
            GameSetupContract.Feature.CODE_LENGTH -> {
                codeLengthsAllowed = values
                binding.codeLengthSeekBar.max = values.size - 1
                gameSetup?.vocabulary?.length
            }
            GameSetupContract.Feature.CODE_CHARACTERS -> {
                codeCharactersAllowed = values
                binding.codeCharactersSeekBar.max = values.size - 1
                gameSetup?.vocabulary?.characters
            }
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