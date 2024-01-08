package com.peaceray.codeword.game.validators

import com.peaceray.codeword.game.validators.Validators.Companion.all
import com.peaceray.codeword.game.validators.Validators.Companion.any
import java.io.File

/**
 * A class for creating Validators, either from input parameters or combinations or other Validators.
 * Ultimately any function mapping strings to booleans may function as a Validator, so there is
 * no need to use this class at all, only the associated Validator interface.
 */
class Validators {

    companion object {

        //region Vocabulary Validators
        //-----------------------------------------------------------------------------------------

        /**
         * A Validator which passes any word that appears in the input set.
         *
         * @param vocabulary The vocabulary available for words.
         */
        fun words(vararg vocabulary: String) = words(vocabulary.toList())

        /**
         * A Validator which passes any word that appears in the input set.
         *
         * @param vocabulary The vocabulary available for words.
         */
        fun words(vocabulary: Iterable<String>): Validator {
            val vocab = vocabulary.toSet()

            return object: Validator {
                override fun invoke(code: String) = code in vocab
            }
        }

        /**
         * A Validator which passes any word that appears in the input file(s), which are
         * assumed to hold one word on each line. Blank lines are discarded.
         *
         * @param vocabularyFiles The vocabulary available for words, as a newline-separated list.
         */
        fun vocabulary(vararg vocabularyFiles: File) = vocabulary(vocabularyFiles.toList())

        /**
         * A Validator which passes any word that appears in the input file(s), which are
         * assumed to hold one word on each line. Blank lines are discarded.
         *
         * @param vocabularyFiles The vocabulary available for words, as a newline-separated list.
         */
        fun vocabulary(vocabularyFiles: Iterable<File>): Validator {
            val wordList = mutableListOf<String>()
            vocabularyFiles.forEach { wordList.addAll(it.readLines()) }

            return words(wordList.filter { it.isNotBlank() })
        }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Word Composition Validators
        //-----------------------------------------------------------------------------------------

        /**
         * A Validator which passes any word whose characters are represented in the input set.
         *
         * @param characters The alphabet available for words.
         */
        fun alphabet(vararg characters: Char) = alphabet(characters.toList())

        /**
         * A Validator which passes any word whose characters are represented in the input set.
         *
         * @param characters The alphabet available for words.
         */
        fun alphabet(characters: Iterable<Char>): Validator {
            val chars = characters.toSet()

            return object: Validator {
                override fun invoke(code: String) = code.all { it in chars }
            }
        }

        /**
         * A Validator which passes any word whose length is exactly that specified.
         *
         * @param length The acceptable length for an input.
         */
        fun length(length: Int): Validator = object: Validator {
            override fun invoke(code: String) = code.length == length
        }

        /**
         * A Validator which passes any word whose length is in the specified range.
         *
         * @param lengthRange The acceptable lengths for an input.
         */
        fun length(lengthRange: IntRange): Validator = object: Validator {
            override fun invoke(code: String) = code.length in lengthRange
        }

        /**
         * A Validator that passes if all characters in the word are distinct (only occur once).
         */
        fun distinctCharacters(): Validator = object: Validator {
            override fun invoke(code: String) = code.toCharArray().distinct().size == code.length
        }

        /**
         * A Validator that passes if the word contains at least one double letter (the same
         * letter 2x or more times in a row).
         */
        fun doubleCharacters(): Validator = object: Validator {
            override fun invoke(code: String) = code.toList().reduce<Char?, Char> { acc, c ->
                if (acc == null || acc == c) null else c
            } == null
        }

        //-----------------------------------------------------------------------------------------
        //endregion

        //region Validator Operators
        //-----------------------------------------------------------------------------------------

        /**
         * A Validator that passes all inputs.
         */
        fun pass(): Validator = object: Validator {
            override fun invoke(code: String) = true
        }

        /**
         * A Validator that fails all inputs.
         */
        fun fail(): Validator = object: Validator {
            override fun invoke(code: String) = false
        }

        /**
         * A Validator that counts the number of input validators which pass the input, and passes
         * if that count is exactly the number specified.
         *
         * Prefer [all] or [any] if appropriate, as they will be more efficient.
         *
         * @param passesCount The acceptable number of validator passes.
         * @param validators The validators to test against inputs.
         */
        fun passes(passesCount: Int, vararg validators: Validator) = passes(passesCount, validators.toList())

        /**
         * A Validator that counts the number of input validators which pass the input, and passes
         * if that count is exactly the number specified.
         *
         * Prefer [all] or [any] if appropriate, as they will be more efficient.
         *
         * @param passesCount The acceptable number of validator passes.
         * @param validators The validators to test against inputs.
         */
        fun passes(passesCount: Int, validators: Iterable<Validator>): Validator {
            val filters = validators.toSet()

            return object: Validator {
                override fun invoke(code: String) = filters.count { it(code) } == passesCount
            }
        }

        /**
         * A Validator that counts the number of input validators which pass the input, and passes
         * if that count falls within the specified range.
         *
         * Prefer [all] or [any] if appropriate, as they will be more efficient.
         *
         * @param passesRange The acceptable range of number of validator passes.
         * @param validators The validators to test against inputs.
         */
        fun passes(passesRange: IntRange, vararg validators: Validator) = passes(passesRange, validators.toList())

        /**
         * A Validator that counts the number of input validators which pass the input, and passes
         * if that count falls within the specified range.
         *
         * Prefer [all] or [any] if appropriate, as they will be more efficient.
         *
         * @param passesRange The acceptable range of number of validator passes.
         * @param validators The validators to test against inputs.
         */
        fun passes(passesRange: IntRange, validators: Iterable<Validator>): Validator {
            val filters = validators.toSet()

            return object: Validator {
                override fun invoke(code: String) = filters.count { it(code) } in passesRange
            }
        }

        /**
         * A Validator that passes iff any input validator passes.
         *
         * @param validators The validators to test against inputs.
         */
        fun any(vararg validators: Validator) = any(validators.toList())

        /**
         * A Validator that passes iff any input validator passes.
         *
         * @param validators The validators to test against inputs.
         */
        fun any(validators: Iterable<Validator>): Validator {
            val filters = validators.toSet()

            return object: Validator {
                override fun invoke(code: String) = filters.any { it(code) }
            }
        }

        /**
         * A Validator that passes iff all input validator pass.
         *
         * @param validators The validators to test against inputs.
         */
        fun all(vararg validators: Validator) = all(validators.toList())

        /**
         * A Validator that passes iff all input validator pass.
         *
         * @param validators The validators to test against inputs.
         */
        fun all(validators: Iterable<Validator>): Validator {
            val filters = validators.toSet()

            return object: Validator {
                override fun invoke(code: String) = filters.all { it(code) }
            }
        }

        /**
         * A Validator that passes iff the input validator fails.
         *
         * @param validator The validator to test against inputs.
         */
        fun not(validator: Validator) = object: Validator {
            override fun invoke(code: String) = !validator(code)
        }

        //-----------------------------------------------------------------------------------------
        //endregion



    }
}