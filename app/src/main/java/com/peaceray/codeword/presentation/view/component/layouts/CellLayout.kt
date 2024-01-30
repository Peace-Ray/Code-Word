package com.peaceray.codeword.presentation.view.component.layouts

import kotlin.math.max
import kotlin.math.min

interface CellLayout {
    val sizeCategory: SizeCategory
    val layoutId: Int
    val size: Float

    enum class SizeCategory {
        FULL,
        LARGE,
        MEDIUM,
        SMALL,
        TINY;

        fun larger(amount: Int = 1): SizeCategory {
            // going "larger" means moving down in ordinal value
            val ordinal = max(0, this.ordinal - amount)
            return values()[ordinal]
        }
        fun smaller(amount: Int = 1): SizeCategory {
            // going "smaller" means moving up in ordinal value
            val ordinal = min(values().size - 1, this.ordinal + amount)
            return values()[ordinal]
        }
    }
}