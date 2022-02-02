package com.peaceray.codeword.game.validators

import java.io.File

class VocabularyValidator(vocabulary: Iterable<String>): (String) -> Boolean {
    private val vocabulary = vocabulary.toList()

    override fun invoke(code: String) = code in vocabulary

    companion object {
        fun fromFiles(vararg filenames: String): VocabularyValidator {
            return fromFiles(filenames.toList())
        }

        fun fromFiles(filenames: Iterable<String>): VocabularyValidator {
            val wordList = mutableListOf<String>()
            filenames.forEach { wordList.addAll(File(it).readLines()) }
            val vocabulary = wordList.filter { it.isNotBlank() }
                .distinct()

            return VocabularyValidator(vocabulary)
        }
    }
}