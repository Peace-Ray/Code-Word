package com.peaceray.codeword.game.bot.modules.generation

import com.peaceray.codeword.game.data.Constraint
import com.peaceray.codeword.game.bot.modules.shared.Candidates

/**
 * A [CandidateGenerationModule] that caches its output and applies additional filters in a
 * monotonic way; that is, such that a superset of [Constraint]s will always generate a subset of
 * [Candidates]. In other words, adding a new [Constraint] can be understood as applying a
 * filter operation to the previously returned [Candidates] fields. The initial generation of
 * [Candidates], and the filtering thereof, is left to subclasses.
 */
abstract class MonotonicCachingGenerationModule: CachingGenerationModule() {
    override fun onCacheMiss(
        previousCandidates: Candidates?,
        previousConstraints: List<Constraint>,
        constraints: List<Constraint>
    ): Candidates {
        return if (previousCandidates == null || previousConstraints.any { it !in constraints }) {
            onCacheMissGeneration(constraints)
        } else {
            onCacheMissFilter(previousCandidates, constraints, constraints.filter { it !in previousConstraints })
        }
    }

    abstract fun onCacheMissGeneration(constraints: List<Constraint>): Candidates
    abstract fun onCacheMissFilter(candidates: Candidates, constraints: List<Constraint>, freshConstraints: List<Constraint>): Candidates
}