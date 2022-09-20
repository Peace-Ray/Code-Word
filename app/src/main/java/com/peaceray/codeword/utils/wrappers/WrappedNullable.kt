package com.peaceray.codeword.utils.wrappers

/**
 * A trivial non-null wrapper around a nullable object. Useful e.g. for passing Nullable objects
 * as non-null Observable results.
 */
data class WrappedNullable<T>(val value: T?) {
    constructor(): this(null)
}

data class MutableWrappedNullable<T>(var value: T?) {
    constructor(): this(null)
}