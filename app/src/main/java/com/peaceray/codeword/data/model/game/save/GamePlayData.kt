package com.peaceray.codeword.data.model.game.save

/**
 * Information specific to the play session of a Game. Should not affect actual Game progress
 * in any visible way; this is a storage location for additional information.
 */
data class GamePlayData(
    val hinting: Boolean = false,
    val hintingSinceRound: Int = -1
) {
    fun with(
        hinting: Boolean? = null,
        hintingSinceRound: Int? = null
    ) = GamePlayData(
        hinting = hinting ?: this.hinting,
        hintingSinceRound = hintingSinceRound ?: this.hintingSinceRound
    )

    val hintingEver: Boolean = hintingSinceRound >= 0
}