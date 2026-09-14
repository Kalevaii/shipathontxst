package com.rommie.app.model

// Private backend/domain record. Never expose through worker-facing repository results.
// Backend must enforce membership, non-self review and uniqueness per completion/reviewer.
data class Review(
    val id: String,
    val completionId: String,
    val reviewerId: String,
    val rating: Int,
    val submittedAt: Long,
) {
    init { require(rating in 1..10) }
}
