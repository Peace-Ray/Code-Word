package com.peaceray.codeword.domain.manager.genie

interface GenieGameSetupSettingsManager {

    /**
     * Allow the player to enter a custom Secret for a guessing game (i.e. a game where
     * their goal is to guess the secret, which they just entered).
     */
    val allowCustomSecret: Boolean

    /**
     * Allow the player to enter custom "Version Check" outcomes, setting minimum and current
     * versions as if returned by the online query.
     */
    val allowCustomVersionCheck: Boolean

}