package com.peaceray.codeword.presentation.view.component.layouts

import android.content.res.Resources
import androidx.annotation.Dimension
import androidx.annotation.LayoutRes
import com.peaceray.codeword.R

data class GuessLetterCellLayout(
    override val sizeCategory: CellLayout.SizeCategory,
    @LayoutRes override val layoutId: Int,
    @Dimension override val size: Float,
    @Dimension val strokeWidth: Float,
    @Dimension val cornerRadius: Float,
    @Dimension val elevation: Float
): CellLayout {
    companion object {
        fun create(resources: Resources, @Dimension widthPerCell: Float = Float.MAX_VALUE): GuessLetterCellLayout {
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
            CellLayout.SizeCategory.FULL -> GuessLetterCellLayout(
                sizeCategory = CellLayout.SizeCategory.FULL,
                layoutId = R.layout.cell_letter_full,
                size = resources.getDimension(R.dimen.guess_letter_cell_full_size),
                strokeWidth = resources.getDimension(R.dimen.guess_letter_cell_full_stroke_width),
                cornerRadius = resources.getDimension(R.dimen.guess_letter_cell_full_corner_radius),
                elevation = resources.getDimension(R.dimen.guess_letter_cell_full_elevation)
            )
            CellLayout.SizeCategory.LARGE -> GuessLetterCellLayout(
                sizeCategory = CellLayout.SizeCategory.LARGE,
                layoutId = R.layout.cell_letter_large,
                size = resources.getDimension(R.dimen.guess_letter_cell_large_size),
                strokeWidth = resources.getDimension(R.dimen.guess_letter_cell_large_stroke_width),
                cornerRadius = resources.getDimension(R.dimen.guess_letter_cell_large_corner_radius),
                elevation = resources.getDimension(R.dimen.guess_letter_cell_large_elevation)
            )
            CellLayout.SizeCategory.MEDIUM -> GuessLetterCellLayout(
                sizeCategory = CellLayout.SizeCategory.MEDIUM,
                layoutId = R.layout.cell_letter_medium,
                size = resources.getDimension(R.dimen.guess_letter_cell_medium_size),
                strokeWidth = resources.getDimension(R.dimen.guess_letter_cell_medium_stroke_width),
                cornerRadius = resources.getDimension(R.dimen.guess_letter_cell_medium_corner_radius),
                elevation = resources.getDimension(R.dimen.guess_letter_cell_medium_elevation)
            )
            CellLayout.SizeCategory.SMALL -> GuessLetterCellLayout(
                sizeCategory = CellLayout.SizeCategory.SMALL,
                layoutId = R.layout.cell_letter_small,
                size = resources.getDimension(R.dimen.guess_letter_cell_small_size),
                strokeWidth = resources.getDimension(R.dimen.guess_letter_cell_small_stroke_width),
                cornerRadius = resources.getDimension(R.dimen.guess_letter_cell_small_corner_radius),
                elevation = resources.getDimension(R.dimen.guess_letter_cell_small_elevation)
            )
            CellLayout.SizeCategory.TINY -> GuessLetterCellLayout(
                sizeCategory = CellLayout.SizeCategory.TINY,
                layoutId = R.layout.cell_letter_tiny,
                size = resources.getDimension(R.dimen.guess_letter_cell_tiny_size),
                strokeWidth = resources.getDimension(R.dimen.guess_letter_cell_tiny_stroke_width),
                cornerRadius = resources.getDimension(R.dimen.guess_letter_cell_tiny_corner_radius),
                elevation = resources.getDimension(R.dimen.guess_letter_cell_tiny_elevation)
            )
        }
    }
}