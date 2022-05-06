package com.peaceray.codeword.game.bot.modules.shared

import kotlin.random.Random

abstract class Seeded(seed: Long) {
    protected val random = Random(seed)
}