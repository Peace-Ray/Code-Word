package com.peaceray.codeword.presentation.view.component.layouts

import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.LayoutRes
import com.peaceray.codeword.R

data class GuessAggregateConstraintCellLayout(
    override val sizeCategory: CellLayout.SizeCategory,
    @LayoutRes override val layoutId: Int,
    @Dimension override val size: Float
): CellLayout {
    companion object {
        fun create(resources: Resources, @Dimension widthPerCell: Float = Float.MAX_VALUE): GuessAggregateConstraintCellLayout {
            val sizeCategory = when {
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_full_size_estimate) -> CellLayout.SizeCategory.FULL
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_large_size_estimate) -> CellLayout.SizeCategory.LARGE
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_medium_size_estimate) -> CellLayout.SizeCategory.MEDIUM
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_small_size_estimate) -> CellLayout.SizeCategory.SMALL
                else -> CellLayout.SizeCategory.TINY
            }
            return create(resources, sizeCategory)
        }

        fun create(resources: Resources, sizeCategory: CellLayout.SizeCategory) = when (sizeCategory) {
            CellLayout.SizeCategory.FULL -> GuessAggregateConstraintCellLayout(
                sizeCategory = CellLayout.SizeCategory.FULL,
                layoutId = R.layout.cell_aggregate_constraint_grid_full,
                size = resources.getDimension(R.dimen.guess_letter_cell_full_size)
            )
            CellLayout.SizeCategory.LARGE -> GuessAggregateConstraintCellLayout(
                sizeCategory = CellLayout.SizeCategory.LARGE,
                layoutId = R.layout.cell_aggregate_constraint_grid_large,
                size = resources.getDimension(R.dimen.guess_letter_cell_large_size)
            )
            CellLayout.SizeCategory.MEDIUM -> GuessAggregateConstraintCellLayout(
                sizeCategory = CellLayout.SizeCategory.MEDIUM,
                layoutId = R.layout.cell_aggregate_constraint_grid_medium,
                size = resources.getDimension(R.dimen.guess_letter_cell_medium_size)
            )
            CellLayout.SizeCategory.SMALL -> GuessAggregateConstraintCellLayout(
                sizeCategory = CellLayout.SizeCategory.SMALL,
                layoutId = R.layout.cell_aggregate_constraint_grid_small,
                size = resources.getDimension(R.dimen.guess_letter_cell_small_size)
            )
            else -> GuessAggregateConstraintCellLayout(
                sizeCategory = CellLayout.SizeCategory.TINY,
                layoutId = R.layout.cell_aggregate_constraint_grid_tiny,
                size = resources.getDimension(R.dimen.guess_letter_cell_tiny_size)
            )
        }
    }
}