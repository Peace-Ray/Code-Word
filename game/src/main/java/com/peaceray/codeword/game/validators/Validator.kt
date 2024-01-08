package com.peaceray.codeword.game.validators

/**
 * A Validator is a class which, when invoked as a function, indicates whether a particular
 * string matches the rules required for use. For instance, it might ensure the provided
 * word includes only characters in a certain set, or appears in a vocabulary list.
 */
interface Validator: (String) -> Boolean {

}