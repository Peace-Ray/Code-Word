package com.peaceray.codeword.domain.managers.game.impl

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.domain.managers.game.GameSetupManager
import javax.inject.Inject

class WordPuzzleGameSetupManager @Inject constructor(): GameSetupManager {
    override fun getSetup(): GameSetup {
        return GameSetup.forWordPuzzle(honest = true, hard = false)
    }
}

class WordEvaluationGameSetupManager @Inject constructor(): GameSetupManager {
    override fun getSetup(): GameSetup {
        return GameSetup.forWordEvaluation(false)
    }
}

class WordDemoGameSetupManager @Inject constructor(): GameSetupManager {
    override fun getSetup(): GameSetup {
        return GameSetup.forWordDemo(honest = true, hard = false)
    }
}