package com.peaceray.codeword.game.validators

class AlphabetValidator(characters: Iterable<Char>): (String) -> Boolean {
    private val characters = characters.toList()

    override fun invoke(code: String) = code.all { it in characters }
}