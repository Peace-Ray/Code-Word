package com.peaceray.codeword.presentation.view.component.layouts

import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.LayoutRes
import com.peaceray.codeword.R
import timber.log.Timber

data class GuessAggregateConstraintCellLayout(
    override val sizeCategory: CellLayout.SizeCategory,
    @LayoutRes override val layoutId: Int,
    @Dimension override val size: Float,
    val pipSizeCategory: CellLayout.SizeCategory,
    @Dimension val pipSize: Float,
    @Dimension val pipStrokeWidth: Float,
    @Dimension val pipMargins: Float,
    @Dimension val pipElevation: Float,
    @Dimension val donutSize: Float,
    @Dimension val donutStrokeWidth: Float,
    @Dimension val donutMargins: Float,
    @Dimension val donutElevation: Float
): CellLayout {
    private constructor(
        sizeCategory: CellLayout.SizeCategory,
        @LayoutRes layoutId: Int,
        @Dimension size: Float,
        pipSizeCategory: CellLayout.SizeCategory,
        @Dimension pipSize: Float,
        @Dimension pipMargins: Float,
        @Dimension donutSize: Float,
        @Dimension donutStrokeWidth: Float,
        @Dimension donutMargins: Float,
        resources: Resources
    ): this (
        sizeCategory = sizeCategory,
        layoutId = layoutId,
        size = size,
        pipSizeCategory,
        pipSize = pipSize,
        pipStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_pip_stroke_width),
        pipMargins = pipMargins,
        pipElevation = resources.getDimension(R.dimen.aggregate_markup_pip_elevation),
        donutSize = donutSize,
        donutStrokeWidth = donutStrokeWidth,
        donutMargins = donutMargins,
        donutElevation = resources.getDimension(R.dimen.aggregate_markup_donut_elevation)
    )

    companion object {
        fun create(resources: Resources, length: Int, @Dimension widthPerCell: Float = Float.MAX_VALUE): GuessAggregateConstraintCellLayout {
            val sizeCategory = when {
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_full_size_estimate) -> CellLayout.SizeCategory.FULL
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_large_size_estimate) -> CellLayout.SizeCategory.LARGE
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_medium_size_estimate) -> CellLayout.SizeCategory.MEDIUM
                widthPerCell >= resources.getDimension(R.dimen.guess_letter_cell_small_size_estimate) -> CellLayout.SizeCategory.SMALL
                else -> CellLayout.SizeCategory.TINY
            }

            return create(resources, length, sizeCategory)
        }

        fun create(resources: Resources, length: Int, sizeCategory: CellLayout.SizeCategory): GuessAggregateConstraintCellLayout {
            val pipSizeCategory = when {
                length == 0 -> sizeCategory.larger(2)
                length <= 4 -> sizeCategory.larger(1)
                length <= 9 -> sizeCategory
                else -> sizeCategory.smaller(1)
            }

            return create(resources, sizeCategory, pipSizeCategory)
        }

        fun create(resources: Resources, sizeCategory: CellLayout.SizeCategory, pipSizeCategory: CellLayout.SizeCategory): GuessAggregateConstraintCellLayout {
            Timber.v("create with size category $sizeCategory pipSizeCategory $pipSizeCategory")

            val layoutId = when (sizeCategory) {
                CellLayout.SizeCategory.FULL -> R.layout.cell_aggregate_constraint_grid_full
                CellLayout.SizeCategory.LARGE -> R.layout.cell_aggregate_constraint_grid_large
                CellLayout.SizeCategory.MEDIUM -> R.layout.cell_aggregate_constraint_grid_medium
                CellLayout.SizeCategory.SMALL -> R.layout.cell_aggregate_constraint_grid_small
                CellLayout.SizeCategory.TINY -> R.layout.cell_aggregate_constraint_grid_tiny
            }

            return when (pipSizeCategory) {
                CellLayout.SizeCategory.FULL -> GuessAggregateConstraintCellLayout(
                    sizeCategory = sizeCategory,
                    layoutId = layoutId,
                    size = resources.getDimension(R.dimen.guess_letter_cell_full_size),
                    pipSizeCategory = pipSizeCategory,
                    pipSize = resources.getDimension(R.dimen.aggregate_markup_pip_full_size),
                    pipMargins = resources.getDimension(R.dimen.aggregate_markup_pip_full_margin),
                    donutSize = resources.getDimension(R.dimen.aggregate_markup_donut_full_size),
                    donutStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_donut_full_stroke_width),
                    donutMargins = resources.getDimension(R.dimen.aggregate_markup_donut_full_margin),
                    resources
                )

                CellLayout.SizeCategory.LARGE -> GuessAggregateConstraintCellLayout(
                    sizeCategory = sizeCategory,
                    layoutId = layoutId,
                    size = resources.getDimension(R.dimen.guess_letter_cell_large_size),
                    pipSizeCategory = pipSizeCategory,
                    pipSize = resources.getDimension(R.dimen.aggregate_markup_pip_large_size),
                    pipMargins = resources.getDimension(R.dimen.aggregate_markup_pip_large_margin),
                    donutSize = resources.getDimension(R.dimen.aggregate_markup_donut_large_size),
                    donutStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_donut_large_stroke_width),
                    donutMargins = resources.getDimension(R.dimen.aggregate_markup_donut_large_margin),
                    resources
                )

                CellLayout.SizeCategory.MEDIUM -> GuessAggregateConstraintCellLayout(
                    sizeCategory = sizeCategory,
                    layoutId = layoutId,
                    size = resources.getDimension(R.dimen.guess_letter_cell_medium_size),
                    pipSizeCategory = pipSizeCategory,
                    pipSize = resources.getDimension(R.dimen.aggregate_markup_pip_medium_size),
                    pipMargins = resources.getDimension(R.dimen.aggregate_markup_pip_medium_margin),
                    donutSize = resources.getDimension(R.dimen.aggregate_markup_donut_medium_size),
                    donutStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_donut_medium_stroke_width),
                    donutMargins = resources.getDimension(R.dimen.aggregate_markup_donut_medium_margin),
                    resources
                )

                CellLayout.SizeCategory.SMALL -> GuessAggregateConstraintCellLayout(
                    sizeCategory = sizeCategory,
                    layoutId = layoutId,
                    size = resources.getDimension(R.dimen.guess_letter_cell_small_size),
                    pipSizeCategory = pipSizeCategory,
                    pipSize = resources.getDimension(R.dimen.aggregate_markup_pip_small_size),
                    pipMargins = resources.getDimension(R.dimen.aggregate_markup_pip_small_margin),
                    donutSize = resources.getDimension(R.dimen.aggregate_markup_donut_small_size),
                    donutStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_donut_small_stroke_width),
                    donutMargins = resources.getDimension(R.dimen.aggregate_markup_donut_small_margin),
                    resources
                )

                else -> GuessAggregateConstraintCellLayout(
                    sizeCategory = sizeCategory,
                    layoutId = layoutId,
                    size = resources.getDimension(R.dimen.guess_letter_cell_tiny_size),
                    pipSizeCategory = pipSizeCategory,
                    pipSize = resources.getDimension(R.dimen.aggregate_markup_pip_tiny_size),
                    pipMargins = resources.getDimension(R.dimen.aggregate_markup_pip_tiny_margin),
                    donutSize = resources.getDimension(R.dimen.aggregate_markup_donut_tiny_size),
                    donutStrokeWidth = resources.getDimension(R.dimen.aggregate_markup_donut_tiny_stroke_width),
                    donutMargins = resources.getDimension(R.dimen.aggregate_markup_donut_tiny_margin),
                    resources
                )
            }
        }
    }
}