package com.peaceray.codeword.utils.extensions

import java.lang.Exception
import java.lang.NumberFormatException

private val DISALLOWED = listOf('0', 'I', 'O', 'l')
private val ALPHABET = listOf(
    ('0'..'9').toList(),
    ('a'..'z').toList(),
    ('A'..'Z').toList()
).flatten()
    .filter { it !in DISALLOWED }

/**
 * Convert this Byte to a fake Base58 encoding. Uses the B58 character set, but does not encode
 * the byte-wise value of the number, just its numeric value. Not compatible with real B58 encodings.
 *
 * @receiver A Byte
 * @return A B58-ish string, possibly with a minus sign appended.
 */
fun Byte.toFakeB58(): String {
    return toLong().toFakeB58()
}

/**
 * Convert this Short to a fake Base58 encoding. Uses the B58 character set, but does not encode
 * the byte-wise value of the number, just its numeric value. Not compatible with real B58 encodings.
 *
 * @receiver A Short
 * @return A B58-ish string, possibly with a minus sign appended, representing the value of [this]
 */
fun Short.toFakeB58(): String {
    return toLong().toFakeB58()
}

/**
 * Convert this Int to a fake Base58 encoding. Uses the B58 character set, but does not encode
 * the byte-wise value of the number, just its numeric value. Not compatible with real B58 encodings.
 *
 * @receiver An Int
 * @return A B58-ish string, possibly with a minus sign appended, representing the value of [this]
 */
fun Int.toFakeB58(): String {
    return toLong().toFakeB58()
}

/**
 * Convert this Long to a fake Base58 encoding. Uses the B58 character set, but does not encode
 * the byte-wise value of the number, just its numeric value. Not compatible with real B58 encodings.
 *
 * @receiver A Long
 * @return A B58-ish string, possibly with a minus sign appended, representing the value of [this]
 */
fun Long.toFakeB58(): String {
    var temp = this
    val chars = mutableListOf<Char>()
    if (temp < 0) {
        chars.add('-')
        temp *= -1
    }
    val div = ALPHABET.size.toLong()
    do {    // do-while so 0 encodes as "1"
        chars.add(ALPHABET[(temp % div).toInt()])
        temp /= ALPHABET.size
    } while (temp > 0)
    return chars.joinToString("")
}

/**
 * Converts this string, which must be a trimmed FakeB58 string, to a Long. Fake B58 uses the
 * B58 character set, but does not encode a byte-wise value of the input data, just the numeric
 * value of a Long. Not compatible with real B58 encodings.
 *
 * @receiver A FakeB58 string, e.g. "a814zH" encoding a Long.
 * @return The Long value from which this B58 string was encoded.
 */
fun String.fromFakeB58(): Long {
    var temp = this
    var number = 0L
    val positive = if (temp.firstOrNull() != '-') true else {
        temp = temp.substring(1)
        false
    }

    val div = ALPHABET.size.toLong()
    temp.reversed().forEach {
        if (it !in ALPHABET) {
            throw NumberFormatException("Illegal characters in Fake B58 string $this")
        }
        number = (number * div) + ALPHABET.indexOf(it)
    }

    return if (positive) number else -number
}

fun String.isFakeB58(): Boolean {
    return try {
        fromFakeB58()
        true
    } catch (err: Exception) {
        false
    }
}