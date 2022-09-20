package com.peaceray.codeword.domain.manager.genie

/**
 * A Manager holding settings for a Genie, i.e. "Game Genie" -- a developer mode allowing
 * advanced configuration or cheats within the app. The most obvious example is the ability
 * to set the game's Secret in advance of play, for the purpose of constructing specific
 * scenarios (e.g. for screenshots).
 */
interface GenieSettingsManager {

    /**
     * Developer Mode blanket-enables all specific settings allowed by the Genie.
     */
    var developerMode: Boolean

}