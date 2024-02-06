package com.peaceray.codeword.data.manager.settings

interface BotSettingsManager {

    /**
     * Measurement of the strength of a solver algorithm, in [0, 1]. 1 is "optimal" play (heuristics
     * may still be used for efficiency but the bot will not make bad plays intentionally).
     */
    var solverStrength: Float

    /**
     * Measurement of the strength of a cheating evaluator's creativity, in [0, 1]. 1 is "optimal"
     * play (heuristics may still be used for efficiency but the bot will not make bad plays
     * intentionally).
     */
    var cheaterStrength: Float

}