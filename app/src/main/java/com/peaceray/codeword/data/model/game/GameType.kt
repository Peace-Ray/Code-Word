package com.peaceray.codeword.data.model.game

import android.os.Parcelable
import com.peaceray.codeword.data.model.code.CodeLanguage
import kotlinx.parcelize.Parcelize

/**
 * A companion data type to [GameSetup]. Where [GameSetup] fully specifies a game in advance of
 * any moves, including randomization seed, normal/hard mode, number of rounds, etc., [GameType]
 * defines a broad category of games where outcomes can be coherently compared. For example, the
 * maximum number of rounds and randomization seed is not particularly significant in defining
 * a broad game "type."
 */
@Parcelize
data class GameType(
    val language: CodeLanguage,
    val length: Int,
    val characters: Int
): Parcelable {

    //region Serialization
    //---------------------------------------------------------------------------------------------
    companion object {
        fun fromString(string: String): GameType {
            val parts = string.split(",")
            return GameType(CodeLanguage.valueOf(parts[0]), parts[1].toInt(), parts[2].toInt())
        }
    }

    override fun toString(): String {
        return "${language.name},${length},${characters}"
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}