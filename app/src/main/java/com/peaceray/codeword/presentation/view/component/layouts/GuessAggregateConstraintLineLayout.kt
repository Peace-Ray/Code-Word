package com.peaceray.codeword.presentation.view.component.layouts

import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.LayoutRes
import com.peaceray.codeword.R

data class GuessAggregateConstraintLineLayout(
    override val sizeCategory: CellLayout.SizeCategory,
    @LayoutRes override val layoutId: Int,
    @Dimension override val size: Float
): CellLayout {
    companion object {
        fun create(resources: Resources, @Dimension widthPerCell: Float = Float.MAX_VALUE) = GuessAggregateConstraintLineLayout(
            sizeCategory = CellLayout.SizeCategory.FULL,
            layoutId = R.layout.cell_aggregate_constraint_line,
            size = resources.getDimension(R.dimen.guess_letter_cell_size)
        )
    }
}