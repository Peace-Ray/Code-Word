package com.peaceray.codeword.presentation.view.component.layouts

interface CellLayout {
    val sizeCategory: SizeCategory
    val layoutId: Int
    val size: Float

    enum class SizeCategory {
        FULL,
        MEDIUM,
        SMALL,
        TINY
    }
}