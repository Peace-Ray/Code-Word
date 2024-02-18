package com.peaceray.codeword.presentation.datamodel.guess

data class GuessEvaluation(val exact: Int, val included: Int, val length: Int, val correct: Boolean) {
    val total = exact + included
}