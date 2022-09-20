package com.peaceray.codeword.game.bot.modules.shared

import com.peaceray.codeword.random.ConsistentRandom

abstract class Seeded(seed: Long) {
    protected val random = ConsistentRandom(seed)
}