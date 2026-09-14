package com.rommie.app.domain

import com.rommie.app.model.Difficulty
import java.math.BigDecimal
import java.math.RoundingMode

fun workloadValue(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> 1
    Difficulty.MEDIUM -> 2
    Difficulty.HARD -> 3
}

// Creation suggestions only: reward is configurable independently of workload.
fun defaultMaxPoints(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> 5
    Difficulty.MEDIUM -> 10
    Difficulty.HARD -> 20
}

/** Central product policy: keep all votes below this review count. */
const val MIN_REVIEWS_FOR_TRIMMING = 5

/**
 * Mean of integer ratings in 1..10; null means no votes/pending.
 * Returns an unrounded IEEE-754 Double. Repeating fractions use its nearest representable value.
 * Integer sums for any JVM List size fit exactly in Double, so input order does not affect the mean.
 */
fun arithmeticRating(ratings: List<Int>): Double? {
    require(ratings.all { it in 1..10 })
    return if (ratings.isEmpty()) null else ratings.average()
}

/**
 * At [MIN_REVIEWS_FOR_TRIMMING] votes, remove one minimum and one maximum occurrence.
 * Sorts a copy; never mutates the input or stored reviews. Smaller groups retain every vote.
 * Uses [arithmeticRating]'s nullable, unrounded Double contract.
 */
fun trimmedRating(ratings: List<Int>): Double? {
    require(ratings.all { it in 1..10 })
    return arithmeticRating(if (ratings.size >= MIN_REVIEWS_FOR_TRIMMING) ratings.sorted().drop(1).dropLast(1) else ratings)
}

/** Pure calculation only: does not finalize a completion, persist a result, or award points. */
fun finalRating(ratings: List<Int>): Double? = trimmedRating(ratings)

// Use the unrounded final rating. Round awarded points once, half up to a whole point.
fun qualityBasedPoints(rating: Double, maxPoints: Int): Int {
    require(rating.isFinite() && rating in 1.0..10.0)
    require(maxPoints >= 0)
    return BigDecimal.valueOf(rating).multiply(BigDecimal.valueOf(maxPoints.toLong()))
        .divide(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValueExact()
}
