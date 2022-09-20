package com.peaceray.codeword.presentation.manager.share.impl

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.domain.manager.game.GameSetupManager
import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.data.ConstraintPolicy
import com.peaceray.codeword.glue.ForActivity
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager
import com.peaceray.codeword.presentation.manager.share.ShareManager
import javax.inject.Inject
import javax.inject.Singleton

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
        val aggregated = gameSetupManager.getCodeLanguageDetails(outcome.type.language).evaluation == ConstraintPolicy.AGGREGATED
        val emojiSwatch = if (aggregated) {
            colorSwatchManager.colorSwatch.emoji.aggregated
        } else {
            colorSwatchManager.colorSwatch.emoji.positioned
        }
        val toEmojiString: Constraint.() -> String = {
            val sorted = if (!aggregated) this.markup else this.markup.sortedBy { when(it) {
                Constraint.MarkupType.EXACT -> 0
                Constraint.MarkupType.INCLUDED -> 1
                Constraint.MarkupType.NO -> 2
            } }

            sorted.joinToString("") { emojiSwatch.emoji(it) }
        }

        val etc = resources.getString(R.string.share_outcome_etc)
        val lines = outcome.constraints.reversed().map { it.toEmojiString() }
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