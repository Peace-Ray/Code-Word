package com.peaceray.codeword.glue.utils.optional

import androidx.annotation.RequiresApi
import java.util.Optional

@RequiresApi(24)
internal class OptionalUtilsAPI24: OptionalUtils.Implementation {

    override fun <T> optionalOrElse(firstOption: Optional<T>, secondOption: T): T = firstOption.orElse(secondOption)

}