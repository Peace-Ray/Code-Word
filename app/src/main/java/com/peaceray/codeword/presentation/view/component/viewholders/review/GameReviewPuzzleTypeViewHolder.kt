package com.peaceray.codeword.presentation.view.component.viewholders.review

import android.view.View
import android.widget.TextView
import com.peaceray.codeword.R
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.data.model.record.GameOutcome
import com.peaceray.codeword.presentation.datamodel.ColorSwatch
import com.peaceray.codeword.presentation.datamodel.GameStatusReview
import com.peaceray.codeword.presentation.manager.color.ColorSwatchManager

/**
 * A ViewHolder for the "puzzle type" section, which lists the puzzle language, length, and
 * other puzzle-identifying characteristics (i.e. things that might affect the solution, such
 * as "bad luck" aka "opponent cheats").
 *
 * This holder does not allow changes to contents (does not display language spinners,
 * length seekbars, etc.), just a description of the current game type. The `mutable` setting
 * is ignored.
 */
class GameReviewPuzzleTypeViewHolder(
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
    private val languageTextView: TextView = itemView.findViewById(R.id.languageTextView)
    private val charactersTextView: TextView = itemView.findViewById(R.id.charactersTextView)
    private val cheatingTextView: TextView = itemView.findViewById(R.id.cheatingTextView)

    init {
        setViewStyle(colorSwatchManager.colorSwatch)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Data Binding
    //---------------------------------------------------------------------------------------------
    override fun setViewContent(review: GameStatusReview, mutable: Boolean) {
        setViewContent(
            review.setup.vocabulary.language,
            review.setup.vocabulary.length,
            review.setup.vocabulary.characters,
            review.setup.evaluator,
            mutable
        )
    }

    override fun setViewStyle(
        review: GameStatusReview,
        mutable: Boolean,
        colorSwatch: ColorSwatch
    ) {
        setViewStyle(colorSwatch)
    }

    override fun setViewContent(outcome: GameOutcome) {
        setViewContent(
            outcome.type.language,
            outcome.type.length,
            outcome.type.characters,
            outcome.evaluator,
            false
        )
    }

    override fun setViewStyle(outcome: GameOutcome, colorSwatch: ColorSwatch) {
        setViewStyle(colorSwatch)
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Helpers
    //---------------------------------------------------------------------------------------------
    private fun setViewContent(language: CodeLanguage, length: Int, characters: Int, evaluator: GameSetup.Evaluator, mutable: Boolean) {
        val context = itemView.context

        val isCode = when (language) {
            CodeLanguage.ENGLISH -> false
            CodeLanguage.CODE -> true
        }

        val templateLanguageCode = context.getString(when(language){
            CodeLanguage.ENGLISH -> R.string.template_english_secret
            CodeLanguage.CODE -> R.string.template_code_secret
        })

        languageTextView.text = context.getString(R.string.game_info_puzzle_language, templateLanguageCode, length)
        charactersTextView.text = context.getString(R.string.game_info_puzzle_characters, characters)

        charactersTextView.visibility = if (isCode) View.VISIBLE else View.GONE
        cheatingTextView.visibility = if (evaluator == GameSetup.Evaluator.CHEATER) View.VISIBLE else View.GONE
    }

    private fun setViewStyle(colorSwatch: ColorSwatch) {
        // no adjustment necessary
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}