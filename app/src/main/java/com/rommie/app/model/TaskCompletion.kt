package com.rommie.app.model

enum class ProofType { PHOTO, VIDEO }

data class ProofSubmission(val id: String, val mediaUrl: String, val type: ProofType) {
    init { require(mediaUrl.isNotBlank()) }
}

// Only finalized aggregates belong here. No individual scores or reviewer identities.
data class VerificationResult(val finalRating: Double, val pointsAwarded: Int) {
    init { require(finalRating.isFinite() && finalRating in 1.0..10.0 && pointsAwarded >= 0) }
}

data class TaskCompletion(
    val id: String,
    val assignmentId: String,
    val householdId: String,
    val completedBy: String,
    val proof: List<ProofSubmission>,
    val submittedAt: Long,
    val result: VerificationResult? = null,
) {
    init { require(proof.isNotEmpty()) }
}
