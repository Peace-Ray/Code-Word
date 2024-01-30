package com.peaceray.codeword.game.data

import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class TestConstraint {
    @Test
    fun constraint_testAllows_partial_regression_1() {
        val secret = "DBBBBCBFFH"
        val constraints = listOf(
            "EEAHGGBAAB",
            "EEAHGGBABE",
            "EEAHGGBAAH",
            "EEAHGGBDDD",
            "EEAHGDCCCC",
            "EEAHFFGHEH",
            "FFFFDCBBFH"
        ).map { Constraint.create(it, secret) }

        assert(constraints.all { it.allows(secret, ConstraintPolicy.AGGREGATED) })
        for (i in 1 until secret.length) {
            val partialSecret = secret.substring(0, i)
            constraints.forEach { c ->
                assert(c.allows(partialSecret, ConstraintPolicy.AGGREGATED, true)) {
                    "constraint $c did not allow $partialSecret"
                }
            }
        }
    }
}