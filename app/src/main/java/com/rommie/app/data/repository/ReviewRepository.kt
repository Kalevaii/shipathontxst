package com.rommie.app.data.repository

interface ReviewRepository {
    /** Immutable submission. Duplicate calls fail; use hasReviewed to recover an uncertain result. */
    suspend fun submitReview(householdId: String, completionId: String, rating: Int)
    suspend fun hasReviewed(householdId: String, completionId: String): Boolean
}
class DuplicateReviewException : IllegalStateException("You already reviewed this completion")
