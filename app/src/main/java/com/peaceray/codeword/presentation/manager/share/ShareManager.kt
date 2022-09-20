package com.peaceray.codeword.presentation.manager.share

import com.peaceray.codeword.data.model.record.GameOutcome

interface ShareManager {

    /**
     * Share this game outcome, possibly through a share target selector
     *
     * @param outcome The game outcome to share
     */
    fun share(outcome: GameOutcome)

    /**
     * Share this text, probably a game outcome
     *
     * @param text The text to share
     */
    fun share(text: String)

    /**
     * Represents this game outcome as sharable text, returning the result
     *
     * @param outcome The game outcome to share
     */
    fun getShareText(outcome: GameOutcome): String

}