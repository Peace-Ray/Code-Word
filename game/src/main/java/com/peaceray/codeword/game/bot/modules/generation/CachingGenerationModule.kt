package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.shared.Candidates
import com.peaceray.codeword.game.bot.modules.shared.Seeded
import kotlin.random.Random

/**
 * A [CandidateGenerationModule] that caches its own output, providing it again (without
 * regeneration) when the same constraints are provided. Subclasses are expected to implement
 * cache miss behavior (the actual generation of new Candidates). Use this superclass if your
 * generation behavior is deterministic given specific [Constraint]s.
 */
abstract class CachingGenerationModule(seed: Long): CandidateGenerationModule, Seeded(seed) {
    private var cachedCandidates: Candidates? = null
    private var cachedConstraints = listOf<Constraint>()

    override fun generateCandidates(constraints: List<Constraint>): Candidates {
        synchronized(this) {
            if (cachedCandidates != null && constraints == cachedConstraints) {
                return cachedCandidates!!
            }

            val candidates = onCacheMiss(cachedCandidates, cachedConstraints, constraints)
            cachedCandidates = candidates
            cachedConstraints = constraints

            return candidates
        }
    }

    abstract fun onCacheMiss(previousCandidates: Candidates?, previousConstraints: List<Constraint>, constraints: List<Constraint>): Candidates
}