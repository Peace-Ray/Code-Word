package com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.language

import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.data.model.code.CodeLanguageDetails
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.seed.SeedVersion
import com.peaceray.codeword.domain.manager.game.setup.impl.setup.versioned.seed.VersionedBySeed
import java.util.SortedSet

/**
 * A Factory for retrieving [CodeLanguageDetails] of a particular language at a particular
 * seed version. An abstract class; concrete implementations should apply versioned game rule logic.
 * This class provides a companion function to select the appropriate [GameTypeFactory] for a given
 * [SeedVersion].
 */
abstract class CodeLanguageDetailsFactory(version: SeedVersion): VersionedBySeed(version) {

    //region Abstract Functionality
    //---------------------------------------------------------------------------------------------

    abstract val languages: SortedSet<CodeLanguage>

    abstract fun get(language: CodeLanguage): CodeLanguageDetails

    //---------------------------------------------------------------------------------------------
    //endregion


    //region Provided Functionality
    //---------------------------------------------------------------------------------------------

    fun has(language: CodeLanguage) = languages.contains(language)

    //---------------------------------------------------------------------------------------------
    //endregion

    //region Companion / Class Functionality
    //---------------------------------------------------------------------------------------------

    companion object {
        private val cachedFactories: MutableMap<SeedVersion, CodeLanguageDetailsFactory> = mutableMapOf()
        private val defaultFactory = CodeLanguageDetailsFactoryV2()

        /**
         * Provide the appropriate CodeLanguageDetailsFactory for this seed. As version updates
         * occur, the acceptable settings for a given code language may change, but we still
         * want legacy seeds to be playable.
         */
        fun getFactory(seedVersion: SeedVersion): CodeLanguageDetailsFactory {
            var factory = cachedFactories[seedVersion]

            if (factory == null) {
                factory = when (seedVersion) {
                    SeedVersion.V1 -> CodeLanguageDetailsFactoryV1()
                    SeedVersion.V2 -> CodeLanguageDetailsFactoryV2()
                }
                cachedFactories[seedVersion] = factory
            }

            return factory
        }

        fun get(seedVersion: SeedVersion, language: CodeLanguage): CodeLanguageDetails = getFactory(seedVersion).get(language)

        fun has(seedVersion: SeedVersion, language: CodeLanguage): Boolean = getFactory(seedVersion).has(language)
    }

    //---------------------------------------------------------------------------------------------
    //endregion

}