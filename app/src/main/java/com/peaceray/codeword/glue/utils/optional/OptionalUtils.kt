package com.peaceray.codeword.glue.utils.optional

import android.os.Build
import java.util.Optional

class OptionalUtils {

    companion object {
        private val implementation = if (Build.VERSION.SDK_INT >= 24) {
            OptionalUtilsAPI24()
        } else {
            OptionalUtilsAPIFallback()
        }

        fun <T> orElse(firstOption: Optional<T>, secondOption: T): T {
            return implementation.optionalOrElse(firstOption, secondOption)
        }
    }

    internal interface Implementation {
        fun <T> optionalOrElse(firstOption: Optional<T>, secondOption: T): T
    }

}