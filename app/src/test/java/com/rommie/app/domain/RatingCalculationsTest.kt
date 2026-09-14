package com.rommie.app.domain

import org.junit.Assert.*
import org.junit.Test

class RatingCalculationsTest {
    private fun expect(expected: Double, ratings: List<Int>) {
        assertEquals(expected, finalRating(ratings)!!, 1e-12)
        assertEquals(expected, trimmedRating(ratings)!!, 1e-12)
    }
    @Test fun emptyRemainsExplicitlyPending() {
        assertNull(arithmeticRating(emptyList()))
        assertNull(trimmedRating(emptyList()))
        assertNull(finalRating(emptyList()))
    }
    @Test fun oneReview() = expect(7.0, listOf(7))
    @Test fun twoReviews() = expect(5.5, listOf(1, 10))
    @Test fun threeReviews() = expect(20.0 / 3, listOf(1, 9, 10))
    @Test fun fourReviewsKeepExtremes() = expect(6.5, listOf(1, 7, 8, 10))
    @Test fun exactlyFiveUsesSpecifiedExample() = expect(25.0 / 3, listOf(8, 8, 9, 9, 1))
    @Test fun moreThanFiveRemovesOnlyTwoVotes() = expect(6.0, listOf(1, 4, 5, 7, 8, 10))
    @Test fun allIdenticalStillHasAMean() = expect(8.0, List(7) { 8 })
    @Test fun lowOutlier() = expect(9.0, listOf(1, 9, 9, 9, 9))
    @Test fun highOutlier() = expect(2.0, listOf(2, 2, 2, 2, 10))
    @Test fun bothOutliers() = expect(6.0, listOf(1, 5, 6, 7, 10))
    @Test fun duplicateMinimumRetainsOneMinimum() = expect(16.0 / 3, listOf(1, 1, 7, 8, 10))
    @Test fun duplicateMaximumRetainsOneMaximum() = expect(21.0 / 3, listOf(1, 5, 6, 10, 10))
    @Test fun duplicateBothExtremes() = expect(5.5, listOf(1, 1, 5, 6, 10, 10))
    @Test fun orderingDoesNotChangeResult() {
        val input = listOf(8, 1, 9, 8, 9, 4)
        for (ordered in listOf(input.sorted(), input.reversed(), listOf(4, 9, 8, 1, 9, 8))) {
            assertEquals(finalRating(input), finalRating(ordered))
            assertEquals(arithmeticRating(input), arithmeticRating(ordered))
        }
    }
    @Test fun oneAccepted() { expect(1.0, listOf(1)); expect(1.0, List(5) { 1 }) }
    @Test fun tenAccepted() { expect(10.0, listOf(10)); expect(10.0, List(5) { 10 }) }
    @Test fun zeroRejectedEvenIfItWouldBeTrimmed() {
        for (calculate in listOf(::arithmeticRating, ::trimmedRating, ::finalRating)) {
            assertThrows(IllegalArgumentException::class.java) { calculate(listOf(0, 5, 6, 7, 8)) }
        }
    }
    @Test fun elevenRejectedEvenIfItWouldBeTrimmed() {
        for (calculate in listOf(::arithmeticRating, ::trimmedRating, ::finalRating)) {
            assertThrows(IllegalArgumentException::class.java) { calculate(listOf(1, 5, 6, 7, 11)) }
        }
    }
    @Test fun neverMutatesCallerList() {
        val input = mutableListOf(8, 8, 9, 9, 1)
        val before = input.toList()
        arithmeticRating(input); trimmedRating(input); finalRating(input)
        assertEquals(before, input)
    }
    @Test fun thresholdPolicyIsCentralizedAtFive() {
        assertEquals(5, MIN_REVIEWS_FOR_TRIMMING)
        expect(7.0, listOf(1, 9, 9, 9))
        expect(9.0, listOf(1, 9, 9, 9, 9))
    }
    @Test fun preservesPrecisionWithoutDisplayRounding() {
        val value = finalRating(listOf(8, 8, 9, 9, 1))!!
        assertEquals(25.0 / 3.0, value, 0.0)
        assertTrue(value > 8.33)
        assertEquals(7.0, arithmeticRating(listOf(8, 8, 9, 9, 1))!!, 0.0)
    }
}
