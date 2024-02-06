package com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned.language

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.data.manager.game.setup.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.game.data.ConstraintPolicy
import java.util.SortedSet

/**
 * This is a versioned game rule class! Do not make ANY modifications to the behavior of this class
 * after [SeedVersion.V1] launches. If the format of CodeLanguageDetails changes, only make alterations
 * necessary to restore the previous behavior exactly. To alter game rules in the future, create a
 * new class for the new [SeedVersion].
 */
internal class CodeLanguageDetailsFactoryV1: CodeLanguageDetailsFactory(SeedVersion.V1) {
    // do not change contents or order
    override val languages: SortedSet<CodeLanguage> = sortedSetOf(
        CodeLanguage.ENGLISH,
        CodeLanguage.CODE
    )

    private val cachedDetails: MutableMap<CodeLanguage, CodeLanguageDetails> = mutableMapOf()
    override fun get(language: CodeLanguage): CodeLanguageDetails {
        var details = cachedDetails[language]
        if (details == null) {
            // to reiterate: do not change ANY of this in an already released version, including
            // but not limited to the order valid secret lengths are specified.
            details = when (language) {
                CodeLanguage.ENGLISH -> CodeLanguageDetails(
                    language = CodeLanguage.ENGLISH,
                    evaluationRecommended = ConstraintPolicy.PERFECT,
                    evaluationsSupported = listOf(ConstraintPolicy.PERFECT),
                    hardModeConstraint = mapOf(Pair(ConstraintPolicy.PERFECT, ConstraintPolicy.POSITIVE)),
                    characters = ('a'..'z').toList(),
                    isEnumeration = false,
                    codeLengthRecommended = 5,
                    codeCharactersRecommended = 26,
                    (3..12).toList(),
                    listOf(26),
                    codeCharacterRepetitionsSupported = listOf(0)
                )
                CodeLanguage.CODE -> CodeLanguageDetails(
                    language = CodeLanguage.CODE,
                    evaluationRecommended = ConstraintPolicy.AGGREGATED,
                    evaluationsSupported = listOf(ConstraintPolicy.AGGREGATED),
                    hardModeConstraint = emptyMap(),
                    characters = ('a'..'p').toList(),
                    isEnumeration = true,
                    codeLengthRecommended = 4,
                    codeCharactersRecommended = 6,
                    (3..8).toList(),
                    (4..16).toList(),
                    codeCharacterRepetitionsSupported = listOf(0)
                )
                else -> throw IllegalArgumentException("$language not supported by $seedVersion")
            }
            cachedDetails[language] = details
        }
        return details
    }
}