package com.peaceray.codeword.presentation.manager.tutorial

import com.peaceray.codeword.data.model.game.GameSetup

/**
 * Indicates whether the user has been Tutorial-ized for specific game modes or overall gameplay.
 */
interface TutorialManager {

    /**
     * Returns whether the user has had ANY GameSetup explained to them.
     *
     * @param gameSetup The GameSetup in question.
     * @return Whether any GameSetup has been explained.
     */
    fun hasExplainedAnything(): Boolean

    /**
     * Reset records such that NOTHING has been tutorialized.
     */
    fun clear()

    /**
     * Returns whether the user has had this GameSetup explained to them.
     *
     * @param gameSetup The GameSetup in question.
     * @return Whether this GameSetup has been explained.
     */
    fun hasExplained(gameSetup: GameSetup): Boolean

    /**
     * Set that the user has had this GameSetup explained to them.
     *
     * @param gameSetup The GameSetup explained.
     * @param explained Whether it has been explained (default: true).
     */
    fun setExplained(gameSetup: GameSetup, explained: Boolean = true)

}