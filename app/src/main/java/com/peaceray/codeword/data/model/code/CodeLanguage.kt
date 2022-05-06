package com.peaceray.codeword.data.model.code

import java.util.*

enum class CodeLanguage {
    ENGLISH {
        override val locale: Locale = Locale.ENGLISH
        override val abbreviation = "en"
        override val aliases = listOf<String>()
    },
    CODE {
        override val locale: Locale? = null
        override val abbreviation = "cw"
        override val aliases = listOf("rune")
    };

    abstract val locale: Locale?
    abstract val abbreviation: String
    abstract val aliases: List<String>
}

fun String.toCodeLanguage(): CodeLanguage {
    val locale = Locale.getDefault()
    val lower = this.toLowerCase(locale).trim()
    val values = CodeLanguage.values()
    return values.firstOrNull { it.name.toLowerCase(locale) == lower }
        ?: values.firstOrNull { lower in it.aliases }
        ?: values.firstOrNull { it.abbreviation == lower }
        ?: values.firstOrNull { lower.startsWith(it.name) }
        ?: values.first { lower.startsWith(it.abbreviation) }
}