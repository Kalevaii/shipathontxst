package com.rommie.app.data.firebase

import com.google.firebase.Timestamp
import org.junit.Assert.*
import org.junit.Test

class ReviewMappersTest {
    private val data = mapOf("householdId" to "home","completionId" to "task","reviewerId" to "peer","rating" to 10L,"submittedAt" to Timestamp(100,0))
    @Test fun mapsExistingReview() {
        val review=ReviewMappers.fromDocument("peer","home","task",data)
        assertEquals("peer",review.id); assertEquals(10,review.rating); assertEquals(100000L,review.submittedAt)
    }
    @Test fun rejectsSpoofedScope() {
        for (field in listOf("householdId","completionId","reviewerId")) assertThrows(IllegalArgumentException::class.java) {
            ReviewMappers.fromDocument("peer","home","task",data+(field to "foreign"))
        }
    }
    @Test fun rejectsNonIntegerOrOutOfRangeRatingAndMissingTime() {
        for (value in listOf(0L,11L)) assertThrows(IllegalArgumentException::class.java) { ReviewMappers.fromDocument("peer","home","task",data+("rating" to value)) }
        assertThrows(IllegalStateException::class.java) { ReviewMappers.fromDocument("peer","home","task",data+("rating" to 5.5)) }
        assertThrows(IllegalStateException::class.java) { ReviewMappers.fromDocument("peer","home","task",data-"submittedAt") }
    }
}
