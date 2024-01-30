package com.peaceray.codeword.presentation.view.component.viewholders.review

import android.content.res.ColorStateList
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.postDelayed
import com.google.android.material.card.MaterialCardView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.game.GameType
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.utils.extensions.getThemeAccentColor
import timber.log.Timber

/**
 * A ViewHolder for the "seed" section, which prominently displays the game seed and a brief
 * description of the puzzle state, along with controls to copy, edit, or refresh the seed.
 * Those latter actions are delegated via a GameSetupListener.
 */
class GameReviewSeedViewHolder(
    itemView: View,
    colorSwatchManager: ColorSwatchManager,
    var listener: GameReviewListener? = null
):
    GameReviewViewHolder(itemView, colorSwatchManager),
    GameReviewViewHolder.SupportsGameStatusReview,
    GameReviewViewHolder.SupportsGameOutcome
{

    //region View
    //---------------------------------------------------------------------------------------------
    private val backgroundView: View = itemView.findViewById(R.id.seedBackgroundView)
    private val seedTextView: TextView = itemView.findViewById(R.id.seed)
    private val seedRandomizeButton: View = itemView.findViewById(R.id.seedRandomizeButton)
    private val gameStatusTextView: TextView = itemView.findViewById(R.id.gameStatusTextView)

    init {
        seedRandomizeButton.setOnClickListener {
            review?.let {
                listener?.onRandomizeSeedClicked(it.seed,this@GameReviewSeedViewHolder)
            }
        }

        backgroundView.setOnClickListener {
            val mutable = mutable ?: false
            val seed = review?.seed ?: outcome?.seed
            seed?.let {
                if (mutable) {
                    listener?.onEditSeedClicked(it, this@GameReviewSeedViewHolder)
                } else {
                    listener?.onCopySeedClicked(it, this@GameReviewSeedViewHolder)
                }
            }
        }

        setViewStyle(emptySet(), false, colorSwatchManager.colorSwatch)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Bindings
    //---------------------------------------------------------------------------------------------
    override fun setViewContent(review: GameStatusReview, mutable: Boolean) {
        setViewContent(
            review.setup.daily,
            review.seed,
            review.setup,
            null,
            review.purpose,
            review.status,
            mutable
        )
    }

    override fun setViewStyle(
        review: GameStatusReview,
        mutable: Boolean,
        colorSwatch: ColorSwatch
    ) {
        setViewStyle(review.notes, mutable, colorSwatch)
    }

    override fun setViewContent(outcome: GameOutcome) {
        setViewContent(
            outcome.daily,
            outcome.seed,
            null,
            outcome.type,
            GameStatusReview.Purpose.EXAMINE,
            GameStatusReview.Status.from(outcome),
            false
        )
    }

    override fun setViewStyle(outcome: GameOutcome, colorSwatch: ColorSwatch) {
        setViewStyle(emptySet(), false, colorSwatch)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Helpers
    //---------------------------------------------------------------------------------------------
    private fun setViewContent(
        daily: Boolean,
        seed: String?,
        setup: GameSetup?,
        gameType: GameType?,
        purpose: GameStatusReview.Purpose,
        status: GameStatusReview.Status,
        mutable: Boolean
    ) {
        val context = itemView.context

        // set visibility
        seedRandomizeButton.visibility = if (mutable) View.VISIBLE else View.GONE
        gameStatusTextView.visibility = when (purpose) {
            GameStatusReview.Purpose.LAUNCH -> View.VISIBLE
            GameStatusReview.Purpose.EXAMINE -> View.GONE
        }

        Timber.v("setViewContent with purpose $purpose")

        // set text
        seedTextView.text = if (!daily) seed else context.getString(R.string.game_setup_daily_seed, seed)
        setStatusText(daily, seed, setup, gameType, purpose, status, mutable)
    }

    private var lastStatusText: String? = null

    private fun setStatusText(
        daily: Boolean,
        seed: String?,
        setup: GameSetup?,
        gameType: GameType?,
        purpose: GameStatusReview.Purpose,
        status: GameStatusReview.Status,
        mutable: Boolean
    ) {
        // when status is LOADING, prefer not to update status text for a beat.
        // If the load completes quickly, it produces a poor visual experience to see it flip
        // between states.
        val statusText = getStatusText(daily, seed, setup, gameType, purpose, status, mutable)
        if (status != GameStatusReview.Status.LOADING || lastStatusText == null) {
            lastStatusText = statusText
            gameStatusTextView.text = statusText
        } else {
            lastStatusText = statusText
            gameStatusTextView.postDelayed(250) {
                if (statusText == lastStatusText) {
                    gameStatusTextView.text = statusText
                }
            }
        }
    }

    private fun getStatusText(
        daily: Boolean,
        seed: String?,
        setup: GameSetup?,
        gameType: GameType?,
        purpose: GameStatusReview.Purpose,
        status: GameStatusReview.Status,
        mutable: Boolean
    ): String {
        val context = itemView.context

        return when (status) {
            GameStatusReview.Status.NEW -> {
                if (daily) {
                    getGameTypeString(
                        setup,
                        gameType,
                        R.string.game_setup_status_new_daily,
                        R.string.game_setup_status_new_daily_no_letter_repetition,
                        R.string.game_setup_status_new_daily_no_specs
                    )
                }
                else context.getString(R.string.game_setup_status_new_seeded)
            }
            GameStatusReview.Status.ONGOING -> {
                if (daily) context.getString(R.string.game_setup_status_ongoing_daily)
                else context.getString(R.string.game_setup_status_ongoing_seeded)
            }
            GameStatusReview.Status.WON -> {
                if (daily) context.getString(R.string.game_setup_status_won_daily)
                else context.getString(R.string.game_setup_status_won_seeded)
            }
            GameStatusReview.Status.LOST -> {
                if (daily) context.getString(R.string.game_setup_status_lost_daily)
                else context.getString(R.string.game_setup_status_lost_seeded)
            }
            GameStatusReview.Status.LOADING -> {
                if (daily) context.getString(R.string.game_setup_status_loading_daily)
                else context.getString(R.string.game_setup_status_loading_seeded)
            }
        }
    }

    private fun getGameTypeString(
        setup: GameSetup?,
        gameType: GameType?,
        stringResId: Int,
        noLetterRepetitionStringResId: Int,
        backupStringResId: Int
    ): String {
        val context = itemView.context

        val length = setup?.vocabulary?.length ?: gameType?.length ?: 0
        val language = getLanguageString(setup?.vocabulary?.language ?: gameType?.language)
        val allowRepetition = (setup?.vocabulary?.characterOccurrences ?: 0) > 1
        val feedbackString = when (setup?.evaluation?.type ?: gameType?.feedback) {
            ConstraintPolicy.AGGREGATED_EXACT -> context.getString(R.string.game_setup_status_feedback_aggregated_exact)
            ConstraintPolicy.AGGREGATED_INCLUDED -> context.getString(R.string.game_setup_status_feedback_aggregated_included)
            ConstraintPolicy.AGGREGATED -> context.getString(R.string.game_setup_status_feedback_aggregated)
            ConstraintPolicy.POSITIVE,
            ConstraintPolicy.ALL,
            ConstraintPolicy.PERFECT -> context.getString(R.string.game_setup_status_feedback_by_letter)
            else -> null
        }

        return when {
            length == 0 || language == null || feedbackString == null -> context.getString(backupStringResId)
            allowRepetition -> context.getString(stringResId, length, language, feedbackString)
            else -> context.getString(noLetterRepetitionStringResId, length, language, feedbackString)
        }
    }

    private fun getLanguageString(language: CodeLanguage?): String? {
        val context = itemView.context

        return when (language) {
            CodeLanguage.ENGLISH -> context.getString(R.string.game_outcome_language_english)
            CodeLanguage.CODE -> context.getString(R.string.game_outcome_language_code)
            null -> null
        }
    }

    private fun setViewStyle(notes: Set<GameStatusReview.Note>, mutable: Boolean, colorSwatch: ColorSwatch) {
        if (backgroundView is MaterialCardView) {
            // use the theme color here, not ColorSwatch, since this component is matched to
            // other mutable views (e.g. CheckBox) that read their color from the current theme.
            backgroundView.strokeColor = if (mutable) itemView.context.getThemeAccentColor() else colorSwatch.evaluation.untried
            backgroundView.setCardBackgroundColor(colorSwatch.container.background)
        } else {
            backgroundView.setBackgroundColor(colorSwatch.container.background)
        }

        if (seedRandomizeButton is TextView) {
            seedRandomizeButton.setTextColor(ColorStateList.valueOf(colorSwatch.container.onBackground))
        } else if (seedRandomizeButton is ImageView) {
            seedRandomizeButton.imageTintList = ColorStateList.valueOf(colorSwatch.container.onBackground)
        }

        seedTextView.setTextColor(colorSwatch.container.onBackground)
        gameStatusTextView.setTextColor(colorSwatch.information.onBackground.tip)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}