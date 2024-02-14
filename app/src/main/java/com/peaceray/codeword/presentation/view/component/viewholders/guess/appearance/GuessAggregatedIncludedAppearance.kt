package com.peaceray.codeword.presentation.view.component.viewholders.guess.appearance

import android.content.Context
import com.peaceray.codeword.presentation.datamodel.guess.GuessEvaluation
import com.peaceray.codeword.presentation.view.component.layouts.GuessAggregateConstraintCellLayout

open class GuessAggregatedIncludedAppearance(context: Context, layout: GuessAggregateConstraintCellLayout): GuessAggregatedExactAppearance(context, layout) {
    override fun getExactCount(evaluation: GuessEvaluation?) = 0
    override fun getIncludedCount(evaluation: GuessEvaluation?) = evaluation?.let { it.exact + it.included } ?: 0
}