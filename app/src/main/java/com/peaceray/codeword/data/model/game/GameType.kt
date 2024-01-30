package com.peaceray.codeword.data.model.game

import android.os.Parcelable
import com.peaceray.codeword.data.model.code.CodeLanguage
import com.peaceray.codeword.game.data.ConstraintPolicy
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
    val characters: Int,
    val characterOccurrences: Int,
    val feedback: ConstraintPolicy
): Parcelable {

    //region Serialization
    //---------------------------------------------------------------------------------------------
    companion object {
        fun fromString(string: String): GameType {
            val parts = string.split(",")

            // v2 includes feedback and character occurrences; can be distinguished by length.
            val isV1 = parts.size <= 3

            val language = CodeLanguage.valueOf(parts[0])
            val length = parts[1].toInt()
            val characters = parts[2].toInt()
            val characterOccurrences= if (isV1) length else parts[3].toInt()
            val feedback = if (!isV1) ConstraintPolicy.valueOf(parts[4]) else when (language) {
                CodeLanguage.ENGLISH -> ConstraintPolicy.PERFECT
                CodeLanguage.CODE -> ConstraintPolicy.AGGREGATED
            }

            return GameType(
                language,
                length,
                characters,
                characterOccurrences,
                feedback
            )
        }
    }

    override fun toString(): String {
        return "${language.name},${length},${characters},${characterOccurrences},${feedback.name}"
    }

    fun toStringHistory(): Set<String> {
        val history = mutableSetOf(toString())

        // v1 strings
        when (language) {
            CodeLanguage.ENGLISH -> if (feedback == ConstraintPolicy.PERFECT && characterOccurrences == length) {
                history.add("${language.name},${length},${characters}")
            }
            CodeLanguage.CODE -> if (feedback == ConstraintPolicy.AGGREGATED && characterOccurrences == length) {
                history.add("${language.name},${length},${characters}")
            }
        }

        return history.toSet()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}