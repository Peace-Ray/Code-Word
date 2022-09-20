package com.peaceray.codeword.utils.extensions

import android.content.res.TypedArray

inline fun <reified T: Enum<T>> TypedArray.getEnum(index: Int, default: T): T {
    return getInt(index, -1).let {
        if (it >= 0) enumValues<T>()[it] else default
    }
}