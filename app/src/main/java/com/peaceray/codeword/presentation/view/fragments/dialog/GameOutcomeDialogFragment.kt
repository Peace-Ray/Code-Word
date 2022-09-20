package com.peaceray.codeword.presentation.view.fragments.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.data.model.record.PerformanceRecord
import com.peaceray.codeword.data.model.record.PlayerStreak
import com.peaceray.codeword.data.model.record.TotalPerformanceRecord
import com.peaceray.codeword.databinding.FragmentGameOutcomeBinding
import com.peaceray.codeword.databinding.GameInfoSectionHistoryBinding
import com.peaceray.codeword.presentation.attach
import com.peaceray.codeword.presentation.contracts.GameOutcomeContract
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.manager.share.ShareManager
import com.peaceray.codeword.presentation.view.component.adapters.HistogramAdapter
import com.peaceray.codeword.presentation.view.component.viewholders.review.*
import dagger.hilt.android.AndroidEntryPoint
import java.util.*
import javax.inject.Inject

@AndroidEntryPoint
class GameOutcomeDialogFragment: CodeWordDialogFragment(R.layout.fragment_game_outcome), GameOutcomeContract.View {

    //region Creation, Arguments, Listener, Controls
    //---------------------------------------------------------------------------------------------
    companion object {
        const val NAME = "GameOutcomeFragment"
        const val ARG_GAME_UUID = "${NAME}_GAME_UUID"
        const val ARG_GAME_SEED = "${NAME}_GAME_SEED"
        const val ARG_GAME_SETUP = "${NAME}_GAME_SETUP"

        fun newInstance(uuid: UUID, seed: String? = null, setup: GameSetup? = null): GameOutcomeDialogFragment {
            val fragment = GameOutcomeDialogFragment()

            val args = Bundle()
            args.putString(ARG_GAME_UUID, uuid.toString())
            args.putString(ARG_GAME_SEED, seed)
            args.putParcelable(ARG_GAME_SETUP, setup)

            fragment.arguments = args
            return fragment
        }
    }

    interface OnInteractionListener {
        fun onOutcomeCreatePuzzleClicked(fragment: GameOutcomeDialogFragment)
    }
    //---------------------------------------------------------------------------------------------
    //endregion


    //region Lifecycle, View Binding, Fields
    //---------------------------------------------------------------------------------------------
    private var _binding: FragmentGameOutcomeBinding? = null
    private val binding get() = _binding!!

    // recyclerview adapter / manager
    @Inject lateinit var histogramAdapter: HistogramAdapter
    lateinit var prefixAdapter: GameOutcomeAdapter
    lateinit var postfixAdapter: GameOutcomeAdapter
    lateinit var adapter: ConcatAdapter
    lateinit var layoutManager: RecyclerView.LayoutManager

    // manager / inflater
    @Inject lateinit var inflater: LayoutInflater
    @Inject lateinit var clipboardManager: ClipboardManager
    @Inject lateinit var shareManager: ShareManager
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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGameOutcomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // prepare scrollable content
        prefixAdapter = GameOutcomeAdapter(listOf(
            GameOutcomeSection.SEED,
            GameOutcomeSection.PUZZLE_TYPE,
            GameOutcomeSection.OUTCOME,
            GameOutcomeSection.HISTORY,
            GameOutcomeSection.PERFORMANCE
        ))
        postfixAdapter = GameOutcomeAdapter(listOf(
            GameOutcomeSection.SPACER,  // adds distance between items and forces dialog width
            GameOutcomeSection.NEW_PUZZLE
        ))
        adapter = ConcatAdapter(prefixAdapter, histogramAdapter, postfixAdapter)
        histogramAdapter.limit = 8  // reevaluated later

        layoutManager = LinearLayoutManager(context)

        binding.recyclerView.adapter = adapter
        binding.recyclerView.layoutManager = layoutManager

        // view
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

    //region View helpers
    //---------------------------------------------------------------------------------------------
    private fun updateViewColors(swatch: ColorSwatch) {
        binding.mainView.setBackgroundColor(swatch.container.background)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region GameOutcome Adapter
    //---------------------------------------------------------------------------------------------
    enum class GameOutcomeSection {
        SEED,
        PUZZLE_TYPE,
        OUTCOME,
        HISTORY,
        PERFORMANCE,
        NEW_PUZZLE,
        SPACER
    }

    inner class GameOutcomeAdapter(val sections: List<GameOutcomeSection>): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var outcome: GameOutcome? = null
        private var gameRecord: PerformanceRecord? = null
        private var totalRecord: TotalPerformanceRecord? = null
        private var streak: PlayerStreak? = null

        private var supportedSections: List<GameOutcomeSection> = sections.filter { it.isSupported() }

        fun setGameOutcome(outcome: GameOutcome) {
            this.outcome = outcome
            this.supportedSections = sections.filter { it.isSupported() }
            this.notifyDataSetChanged()
        }

        fun setGameHistory(
            performanceRecord: PerformanceRecord,
            totalPerformanceRecord: TotalPerformanceRecord,
            playerStreak: PlayerStreak?
        ) {
            this.gameRecord = performanceRecord
            this.totalRecord = totalPerformanceRecord
            this.streak = playerStreak
            this.supportedSections = sections.filter { it.isSupported() }
            this.notifyDataSetChanged()
        }

        fun GameOutcomeSection.isSupported(): Boolean {
            return when (this) {
                GameOutcomeSection.SEED -> outcome?.seed != null
                GameOutcomeSection.PUZZLE_TYPE -> outcome != null
                GameOutcomeSection.OUTCOME -> outcome != null
                GameOutcomeSection.HISTORY -> outcome != null && gameRecord != null && totalRecord != null
                GameOutcomeSection.PERFORMANCE -> gameRecord != null
                GameOutcomeSection.NEW_PUZZLE -> true
                GameOutcomeSection.SPACER -> true
            }
        }

        override fun getItemViewType(position: Int): Int {
            return supportedSections[position].ordinal
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (GameOutcomeSection.values()[viewType]) {
                GameOutcomeSection.SEED -> {
                    val cellView = inflater.inflate(R.layout.game_setup_section_seed_narrow, parent, false)
                    GameReviewSeedViewHolder(cellView, colorSwatchManager, gameSetupListener)
                }
                GameOutcomeSection.PUZZLE_TYPE -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_puzzle_type, parent, false)
                    return GameReviewPuzzleTypeViewHolder(cellView, colorSwatchManager, gameSetupListener)
                }
                GameOutcomeSection.OUTCOME -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_outcome, parent, false)
                    return GameReviewOutcomeViewHolder(cellView, colorSwatchManager, gameSetupListener)
                }
                GameOutcomeSection.HISTORY -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_history, parent, false)
                    return GameOutcomeHistoryViewHolder(cellView)
                }
                GameOutcomeSection.PERFORMANCE -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_performance, parent, false)
                    return GameOutcomePerformanceViewHolder(cellView)
                }
                GameOutcomeSection.NEW_PUZZLE -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_create_puzzle, parent, false)
                    return GameReviewCreatePuzzleViewHolder(cellView, colorSwatchManager, gameSetupListener)
                }
                GameOutcomeSection.SPACER -> {
                    val cellView = inflater.inflate(R.layout.game_info_section_spacing, parent, false)
                    return GameOutcomeSpacingViewHolder(cellView)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val outcome = outcome
            when (holder) {
                is GameReviewViewHolder -> if (outcome != null) holder.bind(outcome)
                is GameOutcomeHistoryViewHolder -> holder.bind(outcome!!, gameRecord!!, totalRecord!!, streak)
                is GameOutcomePerformanceViewHolder -> holder.bind()
                is GameOutcomeSpacingViewHolder -> holder.bind()
            }
        }

        override fun getItemCount() = supportedSections.size
    }

    inner class GameOutcomeHistoryViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        private var binding = GameInfoSectionHistoryBinding.bind(itemView)

        fun bind(
            outcome: GameOutcome,
            performanceRecord: PerformanceRecord,
            totalPerformanceRecord: TotalPerformanceRecord,
            playerStreak: PlayerStreak?
        ) {
            // set title
            binding.titleTextView.text = getString(
                if (outcome.daily) R.string.game_info_section_dailies
                else R.string.game_info_section_history
            )

            // set attempts
            if (outcome.daily) {
                val winPercent = if (performanceRecord.attempts == 0) 0 else (performanceRecord.wins * 100) / performanceRecord.attempts
                binding.attemptsAllTextView.visibility = View.GONE
                binding.attemptsTextView.text = getString(R.string.game_outcome_history_attempts_daily, performanceRecord.attempts, winPercent)
            } else {
                val winPercentAll = if (totalPerformanceRecord.attempts == 0) 0 else (totalPerformanceRecord.wins * 100) / totalPerformanceRecord.attempts
                val winPercent = if (performanceRecord.attempts == 0) 0 else (performanceRecord.wins * 100) / performanceRecord.attempts
                binding.attemptsAllTextView.visibility = View.VISIBLE
                binding.attemptsAllTextView.text = getString(R.string.game_outcome_history_attempts_all, totalPerformanceRecord.attempts, winPercentAll)
                binding.attemptsTextView.text = getString(R.string.game_outcome_history_attempts_type, performanceRecord.attempts, winPercent)
            }

            //  set streak
            if (outcome.daily && playerStreak != null) {
                binding.streakTextView.visibility = View.VISIBLE
                binding.streakTextView.text = getString(R.string.game_outcome_history_streak, playerStreak.currentDaily, playerStreak.bestDaily)
            } else {
                binding.streakTextView.visibility = View.GONE
            }
        }
    }

    inner class GameOutcomePerformanceViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // nothing; just shows a title.
            // invoke this function to ensure compilation errors if requirements are ever added.
        }
    }

    inner class GameOutcomeSpacingViewHolder(itemView: View): RecyclerView.ViewHolder(itemView) {
        fun bind() {
            // nothing; just shows a title.
            // invoke this function to ensure compilation errors if requirements are ever added.
        }
    }

    private val gameSetupListener = object: GameReviewListenerAdapter() {
        override fun onCopySeedClicked(seed: String?, viewHolder: GameReviewViewHolder?) {
            // copy to clipboard
            if (seed != null) {
                val clip = ClipData.newPlainText(getString(R.string.clip_seed_label), seed)
                clipboardManager.setPrimaryClip(clip)
                Toast.makeText(context, getString(R.string.clip_seed_toast), Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCopyOutcomeClicked(outcome: GameOutcome, viewHolder: GameReviewViewHolder?) {
            presenter.onCopyButtonClicked()
        }

        override fun onShareOutcomeClicked(
            outcome: GameOutcome,
            viewHolder: GameReviewViewHolder?
        ) {
            presenter.onShareButtonClicked()
        }

        override fun onCreatePuzzleClicked(
            review: GameStatusReview?,
            viewHolder: GameReviewViewHolder?
        ) {
            listener?.onOutcomeCreatePuzzleClicked(this@GameOutcomeDialogFragment)
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region CodeGameContract
    //---------------------------------------------------------------------------------------------
    @Inject lateinit var presenter: GameOutcomeContract.Presenter

    override fun getGameUUID(): UUID = UUID.fromString(requireArguments().getString(ARG_GAME_UUID))

    override fun getGameSeed(): String? = requireArguments().getString(ARG_GAME_SEED)

    override fun getGameSetup(): GameSetup? = requireArguments().getParcelable(ARG_GAME_SETUP)

    override fun setGameOutcome(outcome: GameOutcome) {
        // prefer short histograms if possible. For manual turn counts, display that many.
        histogramAdapter.limit = if (outcome.rounds == 0 || outcome.rounds > 8) 8 else outcome.rounds

        // bind to adapters
        prefixAdapter.setGameOutcome(outcome)
        postfixAdapter.setGameOutcome(outcome)
        histogramAdapter.bind(outcome)
    }

    override fun setGameHistory(
        performanceRecord: PerformanceRecord,
        totalPerformanceRecord: TotalPerformanceRecord,
        playerStreak: PlayerStreak?
    ) {
        //  bind to adapters
        prefixAdapter.setGameHistory(performanceRecord, totalPerformanceRecord, playerStreak)
        postfixAdapter.setGameHistory(performanceRecord, totalPerformanceRecord, playerStreak)
        histogramAdapter.bind(performanceRecord.winningTurnCounts)
    }

    override fun share(outcome: GameOutcome) {
        shareManager.share(outcome)
    }

    override fun copy(outcome: GameOutcome) {
        val text = shareManager.getShareText(outcome)
        val clip = ClipData.newPlainText(getString(R.string.clip_outcome_label), text)
        clipboardManager.setPrimaryClip(clip)
        Toast.makeText(context, getString(R.string.clip_outcome_toast), Toast.LENGTH_SHORT).show()
    }

    override fun close() {
        dismiss()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}