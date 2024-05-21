package com.peaceray.codeword.glue.utils.optional

import java.util.Optional

internal class OptionalUtilsAPIFallback: OptionalUtils.Implementation {
    override fun <T> optionalOrElse(firstOption: Optional<T>, secondOption: T) = secondOption
}