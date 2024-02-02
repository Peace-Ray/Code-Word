package com.peaceray.codeword.presentation.manager.share.impl

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.domain.manager.game.setup.GameSetupManager
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.glue.ForActivity
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.manager.share.ShareManager
import javax.inject.Inject

class ShareManagerImpl @Inject constructor(
    @ForActivity private val context: Context,
    @ForActivity private val resources: Resources,
    private val gameSetupManager: GameSetupManager,
    private val colorSwatchManager: ColorSwatchManager
): ShareManager {

    override fun share(outcome: GameOutcome) {
        share(getShareText(outcome))
    }

    override fun share(text: String) {
        val sendIntent: Intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }

        val shareIntent = Intent.createChooser(sendIntent, null)
        context.startActivity(shareIntent)
    }

    override fun getShareText(outcome: GameOutcome) = buildString {
        val app = resources.getString(R.string.app_name)
        val seed = outcome.seed
        val roundsAvailable = if (outcome.rounds >= 1000) "∞" else "${outcome.rounds}"
        val roundsPair = when(outcome.outcome) {
            GameOutcome.Outcome.WON -> Pair("${outcome.round}", roundsAvailable)
            GameOutcome.Outcome.LOST -> Pair(resources.getString(R.string.share_outcome_rounds_lost), roundsAvailable)
            GameOutcome.Outcome.FORFEIT -> Pair(resources.getString(R.string.share_outcome_rounds_lost), roundsAvailable)
            GameOutcome.Outcome.LOADING -> Pair(resources.getString(R.string.share_outcome_rounds_loading), roundsAvailable)
        }
        val rounds = resources.getString(if (outcome.hard) {
            R.string.share_outcome_rounds_hard
        } else {
            R.string.share_outcome_rounds
        }, roundsPair.first, roundsPair.second)

        // first line: app name and game info
        append(if (seed == null) {
            resources.getString(R.string.share_outcome_template_no_seed, app, rounds)
        } else {
            resources.getString(R.string.share_outcome_template_seed, app, seed, rounds)
        })

        // line break
        appendLine()
        appendLine()

        // constraints
        val maxLength = 160
        val constraintStart = length
        val aggregated = outcome.type.feedback.isByWord()
        val emojiSwatch = if (aggregated) {
            colorSwatchManager.colorSwatch.emoji.aggregated
        } else {
            colorSwatchManager.colorSwatch.emoji.positioned
        }
        val toEmojiString: Constraint.() -> String = {
            val reduced = when (outcome.type.feedback) {
                ConstraintPolicy.IGNORE -> emptyList()
                ConstraintPolicy.AGGREGATED_EXACT -> this.markup.map { if (it == Constraint.MarkupType.EXACT) it else Constraint.MarkupType.NO }
                ConstraintPolicy.AGGREGATED_INCLUDED -> this.markup.map { if (it != Constraint.MarkupType.NO) Constraint.MarkupType.INCLUDED else Constraint.MarkupType.NO }
                ConstraintPolicy.AGGREGATED,
                ConstraintPolicy.POSITIVE,
                ConstraintPolicy.ALL,
                ConstraintPolicy.PERFECT -> this.markup
            }
            val sorted = when (outcome.type.feedback) {
                ConstraintPolicy.IGNORE -> reduced
                ConstraintPolicy.AGGREGATED_EXACT,
                ConstraintPolicy.AGGREGATED_INCLUDED,
                ConstraintPolicy.AGGREGATED -> reduced.sortedBy { when(it) {
                    Constraint.MarkupType.EXACT -> 0
                    Constraint.MarkupType.INCLUDED -> 1
                    Constraint.MarkupType.NO -> 2
                } }
                ConstraintPolicy.POSITIVE,
                ConstraintPolicy.ALL,
                ConstraintPolicy.PERFECT -> reduced
            }

            sorted.joinToString("") { emojiSwatch.emoji(it) }
        }

        val etc = resources.getString(R.string.share_outcome_etc)
        val lines = outcome.constraints.reversed().map { it.toEmojiString() }.filter { it.isNotBlank() }
        for (index in lines.indices) {
            // last constraint first
            val line = lines[index]
            if (length + line.length + etc.length + 1 <= maxLength) {
                // include this line
                if (index > 0) insert(constraintStart, "\n")
                insert(constraintStart, line)
            } else {
                // end here; no room for more lines
                if (index > 0) insert(constraintStart, "\n")
                insert(constraintStart, etc)
                break
            }
        }
    }


}