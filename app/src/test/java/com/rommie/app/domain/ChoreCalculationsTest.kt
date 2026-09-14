package com.rommie.app.domain

import com.rommie.app.model.Difficulty
import org.junit.Assert.*
import org.junit.Test

class ChoreCalculationsTest {
    @Test fun difficultyDefaults() {
        assertEquals(listOf(1, 2, 3), Difficulty.entries.map(::workloadValue))
        assertEquals(listOf(5, 10, 20), Difficulty.entries.map(::defaultMaxPoints))
    }
    @Test fun noVotesRemainPending() { assertNull(finalRating(emptyList())) }
    @Test fun smallHouseholdsKeepEveryVote() {
        assertEquals(1.0, finalRating(listOf(1))!!, 0.0)
        assertEquals(5.5, finalRating(listOf(1, 10))!!, 0.0)
        assertEquals(7.0, finalRating(listOf(9, 9, 3))!!, 0.0)
        assertEquals(7.0, finalRating(listOf(9, 9, 9, 1))!!, 0.0)
    }
    @Test fun trimsExactlyOneOfEachExtreme() {
        assertEquals(25.0 / 3, finalRating(listOf(9, 9, 8, 8, 1))!!, 1e-12)
        assertEquals(19.0 / 3, trimmedRating(listOf(1, 1, 9, 9, 9))!!, 1e-12)
        assertEquals(8.0, finalRating(List(5) { 8 })!!, 0.0)
    }
    @Test fun awardsQualityProportionAndRoundsHalfUp() {
        assertEquals(14, qualityBasedPoints(7.0, 20))
        assertEquals(18, qualityBasedPoints(9.0, 20))
        assertEquals(20, qualityBasedPoints(10.0, 20))
        assertEquals(3, qualityBasedPoints(5.0, 5))
        assertEquals(17, qualityBasedPoints(finalRating(listOf(9, 9, 8, 8, 1))!!, 20))
        assertEquals(0, qualityBasedPoints(10.0, 0))
        assertEquals(Int.MAX_VALUE, qualityBasedPoints(10.0, Int.MAX_VALUE))
    }
    @Test fun rejectsInvalidInputs() {
        listOf(0, 11).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { finalRating(listOf(bad)) }
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 10.1).forEach { bad ->
            assertThrows(IllegalArgumentException::class.java) { qualityBasedPoints(bad, 20) }
        }
        assertThrows(IllegalArgumentException::class.java) { qualityBasedPoints(8.0, -1) }
    }
}
