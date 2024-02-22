package com.peaceray.codeword.data.model.game.save

/**
 * Information specific to the play session of a Game. Should not affect actual Game progress
 * in any visible way; this is a storage location for additional information.
 */
data class GamePlayData(
    val hinting: Boolean = false,
    val hintingEver: Boolean = false
) {
    fun with(
        hinting: Boolean? = null,
        hintingEver: Boolean? = null
    ) = GamePlayData(
        hinting = hinting ?: this.hinting,
        hintingEver = hintingEver ?: this.hintingEver
    )
}