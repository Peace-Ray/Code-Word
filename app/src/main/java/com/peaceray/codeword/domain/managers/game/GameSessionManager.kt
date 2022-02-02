package com.peaceray.codeword.domain.managers.game

import com.peaceray.codeword.data.model.game.GameSetup
import com.peaceray.codeword.game.Game
import com.peaceray.codeword.game.bot.Evaluator
import com.peaceray.codeword.game.bot.Solver

interface GameSessionManager {
    fun createGame(setup: GameSetup): Game
    fun getSolver(setup: GameSetup): Solver
    fun getEvaluator(setup: GameSetup): Evaluator
    fun getCodeCharacters(setup: GameSetup): Iterable<Char>
}