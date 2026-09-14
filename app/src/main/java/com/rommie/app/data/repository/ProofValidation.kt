package com.rommie.app.data.repository

import com.rommie.app.data.firebase.FirestorePaths
import com.rommie.app.model.ProofSubmission
import com.rommie.app.model.ProofType

object ProofValidation {
    const val PHOTO_LIMIT = 5L * 1024 * 1024
    const val VIDEO_LIMIT = 20L * 1024 * 1024
    fun maxBytes(type: ProofType) = if (type == ProofType.PHOTO) PHOTO_LIMIT else VIDEO_LIMIT
    fun validateUpload(type: ProofType, contentType: String, size: Long) {
        require(size in 1..maxBytes(type)) { "Proof is empty or exceeds its size limit" }
        require(if (type == ProofType.PHOTO) contentType in setOf("image/jpeg", "image/png") else contentType == "video/mp4")
    }
    fun path(householdId: String, assignmentId: String, workerUid: String, proofId: String): String {
        FirestorePaths.completion(householdId, assignmentId)
        require(proofId.matches(Regex("[A-Za-z0-9_-]{1,128}")))
        return FirestorePaths.proof(householdId, assignmentId, workerUid, proofId)
    }
    fun validate(householdId: String, assignmentId: String, workerUid: String, proof: ProofSubmission) {
        require(proof.mediaUrl == path(householdId, assignmentId, workerUid, proof.id)) {
            "Proof must use this assignment's private worker-specific Storage path"
        }
    }
}
