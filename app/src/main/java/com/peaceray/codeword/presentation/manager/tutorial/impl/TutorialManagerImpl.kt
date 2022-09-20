package com.peaceray.codeword.presentation.manager.tutorial.impl

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.glue.ForApplication
import com.peaceray.codeword.presentation.manager.tutorial.TutorialManager
import javax.inject.Inject

/**
 * A manager class recording whether specific game modes (or other features) have been tutorialized.
 * Lives in the Presentation layer, since changes to the tutorial format may invalidate previous
 * tutorialization or require different granularity in tracking explanations. Although this Manager
 * does not explicitly depend on the tutorial's presentation (view) implementation, there is a
 * mutual design dependency between the two.
 *
 */
class TutorialManagerImpl @Inject constructor(
    @ForApplication private val context: Context
): TutorialManager {

    //region SharedPreferences and Preference Mapping
    //---------------------------------------------------------------------------------------------
    companion object {
        // prefs file
        private const val SHARED_PREFERENCES_FILE = "TutorialPrefs"

        // versioning (in case tutorials change)
        private const val VERSION_UNSET = 0
        private const val VERSION = 1

        // keys
        private const val KEY_COMP_GAME = "game"
        private const val KEY_COMP_VOCABULARY_TYPE = "vocabulary_type"

        private fun getKey() = KEY_COMP_GAME

        private fun getKey(gameSetup: GameSetup) = getKeys(gameSetup).maxByOrNull { it.length }!!

        private fun getKeys(gameSetup: GameSetup): List<String> {
            // explicitly map to strings to ensure a refactor elsewhere does not break tutorial record
            val vocab = when (gameSetup.vocabulary.type) {
                GameSetup.Vocabulary.VocabularyType.LIST -> "LIST"
                GameSetup.Vocabulary.VocabularyType.ENUMERATED -> "ENUMERATED"
            }

            // note: if this list grows, update get/setExplained to consider the appropriate keys.
            return listOf(
                KEY_COMP_GAME,
                "${KEY_COMP_GAME}_${KEY_COMP_VOCABULARY_TYPE}_${vocab}"
            )
        }
    }

    /**
     * The SharedPreferences instance used for tutorial. Does not use the default SharedPreferences
     * file, since "clear" events might need to iterate through all stored keys.
     */
    private val preferences: SharedPreferences by lazy {
        context.getSharedPreferences(SHARED_PREFERENCES_FILE, 0)
    }


    //---------------------------------------------------------------------------------------------
    //endregion

    //region TutorialManager Interface
    //---------------------------------------------------------------------------------------------
    override fun hasExplainedAnything() = hasExplained(getKey())

    override fun clear() {
        val keys = preferences.all.keys
        preferences.edit {
            keys.forEach { remove(it) }
        }
    }

    override fun hasExplained(gameSetup: GameSetup) = hasExplained(getKey(gameSetup))

    override fun setExplained(gameSetup: GameSetup, explained: Boolean) {
        if (explained) {
            // two keys: any game, and this vocabulary type
            val keys = getKeys(gameSetup)
            preferences.edit {
                keys.forEach { putInt(it, VERSION) }
            }
        } else {
            // unset this specific game type, and unset "any" game iff it was the only type entered.
            // note: there are only two keys per game mode: any game, and that vocabulary type
            val keyAny = getKey()
            val keyGame = getKey(gameSetup)
            val keys = preferences.all.keys
            val maxVersion = keys.filter { it != keyAny && it != keyGame }
                .maxOfOrNull { preferences.getInt(it, VERSION_UNSET) } ?: VERSION_UNSET

            preferences.edit {
                remove(keyGame)             // remove the indicated game mode
                putInt(keyAny, maxVersion)  // downgrade(?) to maximum version otherwise displayed
            }
        }
    }

    private fun hasExplained(key: String) = preferences.getInt(key, VERSION_UNSET) > VERSION_UNSET
    //---------------------------------------------------------------------------------------------
    //endregion

}